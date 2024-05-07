package taskmaster.workflow.db

import cats.effect.IO
import fabric.Json
import fabric.rw._
import lightdb.Id
import taskmaster.scheduler.Scheduler
import taskmaster.task.TaskId

import java.time.Instant

/**
 * TaskInstance represents the information for a task to run within a workflow. It is persisted in a Workflow.
 *
 * @param name the task name
 * @param payload the task's payload
 * @param result the task's successful result
 * @param taskId the TaskId
 * @param dependsOn the TaskIds within the Workflow that must successfully complete before this task can run
 * @param workflowId the Workflow associated with this task
 */
case class TaskInstance(name: String,
                        payload: Json,
                        result: Option[Json],
                        taskId: TaskId,
                        dependsOn: Set[TaskId],
                        workflowId: Id[Workflow]) {
  /**
   * Convenience method to determine if this task is ready to be run based on resolved dependencies
   */
  def canRun(resolved: Set[TaskId]): Boolean = dependsOn.forall(resolved.contains)

  /**
   * Convenience method to schedule the execution of this task to execute
   *
   * @param applicationName the associated application name
   * @param when the instant this should run
   */
  def schedule(applicationName: String, when: Instant): IO[Unit] = Scheduler.schedule(applicationName, name, payload, when, taskId)
}

object TaskInstance {
  implicit val rw: RW[TaskInstance] = RW.gen
}