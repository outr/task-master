package taskmaster.workflow.db

import cats.effect.IO
import fabric.Json
import lightdb.Id
import taskmaster.task.TaskId

object DBManager {
  /**
   * Takes in the task that successfully completed and updates the workflow in the database and provides a list of tasks
   * to be run next based on dependency resolution.
   *
   * @param workflowId the workflow
   * @param taskId the completed task
   * @param result the result to apply to the task instance
   * @param continue true if the workflow should schedule tasks to run next
   * @return IO[WorkflowUpdate]
   */
  def complete(workflowId: Id[Workflow], taskId: TaskId, result: Json, continue: Boolean): IO[WorkflowUpdate] = Workflow.withLock(workflowId) { implicit docLock =>
    for {
      workflow <- Workflow(workflowId)
      (completing, scheduled) = workflow.scheduled.partition(_.taskId == taskId)
      modified = completing.map(ti => ti.copy(result = Some(result)))
      completed = workflow.completed ::: modified
      completedIds = completed.map(_.taskId).toSet
      (newlyScheduled, queue) = workflow.queue.partition(_.canRun(completedIds))
      updated <- Workflow.set(workflow.copy(
        queue = queue,
        scheduled = scheduled ::: newlyScheduled,
        completed = completed
      ))
    } yield WorkflowUpdate(
      workflow = updated,
      scheduled = newlyScheduled
    )
//    val query =
//      aql"""
//          FOR w IN workflows
//          FILTER w._id == $workflowId
//          LET completing = (
//              FOR t IN w.scheduled
//              FILTER t.taskId == $taskId
//              RETURN t
//          )
//          LET modified = (
//              FOR t IN completing
//              RETURN MERGE(t, {
//                result: $result
//              })
//          )
//          LET completed = APPEND(w.completed, modified)
//          LET completedIds = (
//              FOR t IN completed
//              RETURN t.taskId
//          )
//          LET scheduled = (
//              FOR t IN w.queue
//              FILTER $continue
//              FILTER LENGTH(MINUS(t.dependsOn, completedIds)) == 0
//              RETURN t
//          )
//          LET updated = MERGE(w, {
//              queue: MINUS(w.queue, scheduled),
//              scheduled: APPEND(MINUS(w.scheduled, completing), scheduled),
//              completed: completed
//          })
//          UPDATE w._key WITH updated IN workflows
//          RETURN {
//              workflow: updated,
//              scheduled: scheduled
//          }
//         """
//    WorkflowDB.query[WorkflowUpdate](query).one
  }
}