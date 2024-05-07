package taskmaster.workflow.db

import cats.effect.IO
import cats.implicits._
import fabric.Json
import fabric.rw._
import lightdb.model.RecordDocumentCollection
import lightdb.{Document, Id, RecordDocument}
import taskmaster.task.{Task, TaskId, TaskInstanceCreator}
import taskmaster.workflow.WorkflowPayload

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

/**
 * Workflow represents the persistent state of a workflow in the database.
 *
 * @param queue the tasks queued to be run
 * @param scheduled the tasks that have been scheduled to be run but not completed
 * @param completed the tasks that have successfully completed
 * @param _id the unique identifier
 */
case class Workflow(queue: List[TaskInstance],
                    scheduled: List[TaskInstance] = Nil,
                    completed: List[TaskInstance] = Nil,
                    created: Long = System.currentTimeMillis(),
                    modified: Long = System.currentTimeMillis(),
                    _id: Id[Workflow] = Id()) extends RecordDocument[Workflow] {
  /**
   * Represents the completed TaskIds
   */
  lazy val resolved: Set[TaskId] = completed.map(_.taskId).toSet

  /**
   * The available tasks that can be run based on the current state of the workflow
   */
  lazy val availableTasks: List[TaskInstance] = queue.filter(_.canRun(resolved))

  /**
   * Attempts to find the TaskInstance by TaskId in this Workflow. Scans queue, scheduledTasks, and completedTasks.
   */
  def taskInstance(taskId: TaskId): Option[TaskInstance] = queue.find(_.taskId == taskId)
    .orElse(scheduled.find(_.taskId == taskId))
    .orElse(completed.find(_.taskId == taskId))

  /**
   * Convenience method to schedule a workflow to run its next available task using a FiniteDuration.
   *
   * @param applicationName the application name associated with the workflow
   * @param when the FiniteDuration of how long in the future to run
   * @return IO[Workflow]
   */
  def schedule(applicationName: String, when: FiniteDuration): IO[Workflow] =
    schedule(applicationName, Instant.ofEpochMilli(when.toMillis))

  /**
   * Convenience method to schedule a workflow to run its next available task using an Instant.
   *
   * @param applicationName the application name associated with the workflow
   * @param when            the FiniteDuration of how long in the future to run
   * @return IO[Workflow]
   */
  def schedule(applicationName: String, when: Instant): IO[Workflow] = for {
    // Update the workflow moving the available tasks out of queue and into firedTasks
    updated <- IO(copy(
      queue = queue.filterNot(availableTasks.contains),
      scheduled = scheduled ::: availableTasks
    ))
    // Update the new workflow to the database
    _ <- Workflow.set(updated)
    // Schedule each available task to be run
    _ <- availableTasks.traverse(_.schedule(applicationName, when))
  } yield {
    updated
  }

  /**
   * Called by WorkflowTasks when they successfully complete to trigger the next phase of the workflow execution.
   *
   * @param applicationName the application name associated with the workflow
   * @param taskId          the TaskId that successfully completed
   * @param result          the resulting JSON from the task run
   * @param continue        whether the workflow should continue after successful completion
   * @return IO[Workflow]
   */
  def complete(applicationName: String, taskId: TaskId, result: Json, continue: Boolean): IO[Workflow] = for {
    update <- DBManager.complete(_id, taskId, result, continue)
    _ <- update.scheduled.traverse(_.schedule(applicationName, Instant.now()))
  } yield {
    update.workflow
  }
}

object Workflow extends RecordDocumentCollection[Workflow]("workflows", WorkflowDB) {
  override implicit val rw: RW[Workflow] = RW.gen

  override val autoCommit: Boolean = true

  /**
   * Convenience method to create a Workflow based on TaskInstanceCreators
   *
   * @param tasks the TaskInstanceCreators to create the TaskInstances
   * @param when the instant it should run (defaults to now)
   * @param applicationName the application name for the workflow
   * @tparam Payload the payload
   * @return IO[Workflow]
   */
  def create[Payload <: WorkflowPayload](tasks: List[TaskInstanceCreator[Payload]],
                                         when: Instant = Instant.now(),
                                         applicationName: String = Task.applicationName): IO[Workflow] = {
    val workflowId = tasks.head.payload.workflowId
    val instances = tasks.map { c =>
      assert(c.payload.workflowId == workflowId, s"Expected ${c.payload} workflowId to be $workflowId, but wasn't")
      TaskInstance(
        name = c.handler.taskName,
        payload = c.payload.json(c.handler.rw),
        result = None,
        taskId = c.taskId,
        dependsOn = c.dependencies.map(_.taskId),
        workflowId = c.payload.workflowId
      )
    }
    val workflow = Workflow(
      queue = instances,
      _id = workflowId
    )
    for {
      // Insert the new Workflow into the database
      _ <- Workflow.set(workflow)
      updated <- workflow.schedule(applicationName, when)
    } yield {
      updated
    }
  }
}