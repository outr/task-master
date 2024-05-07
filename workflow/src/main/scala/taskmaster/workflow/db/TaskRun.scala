package taskmaster.workflow.db

import fabric.Json
import fabric.rw._
import lightdb.model.Collection
import lightdb.sqlite.{SQLIndexedField, SQLiteSupport}
import lightdb.{Document, Id}
import taskmaster.task.{ExecutionResult, RunTask, TaskId}

/**
 * TaskRun represents the logged execution of a LoggedTask in the database.
 *
 * @param id the task's runId
 * @param task the RunTask data for the task
 * @param result the execution result
 * @param _id a unique identifier for this record
 */
case class TaskRun(id: String,
                   task: RunTask[Json],
                   result: ExecutionResult,
                   _id: Id[TaskRun] = Id()) extends Document[TaskRun]

object TaskRun extends Collection[TaskRun]("taskRuns", WorkflowDB) with SQLiteSupport[TaskRun] {
  override implicit val rw: RW[TaskRun] = RW.gen

  override val autoCommit: Boolean = true

  val taskId: SQLIndexedField[TaskId, TaskRun] = index("taskId", doc => Some(doc.task.taskId))
}
