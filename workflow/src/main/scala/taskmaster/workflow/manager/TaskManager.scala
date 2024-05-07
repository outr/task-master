package taskmaster.workflow.manager

import cats.effect.IO
import taskmaster.task.{ExecutionResult, TaskId, TaskResult}
import taskmaster.workflow.db.{TaskRun, WorkflowDB}

/**
 * TaskManager provides convenience methods to access information related to a Task in the database
 */
object TaskManager {
  /**
   * Retrieve the status for a TaskId. This is mostly used to access the failure count.
   *
   * Note: This utilizes TaskRuns in the database which are only recorded by LoggedTask, so this will never give useful
   * results if the task does not mix-in LoggedTask.
   *
   * @param taskId the task's TaskId
   * @return IO[TaskStatus]
   */
  def status(taskId: TaskId): IO[TaskStatus] = TaskRun.withSearchContext { implicit context =>
    TaskRun.query
      .filter(TaskRun.taskId === taskId)
      .stream
      .scan(TaskStatus(success = false, failures = 0))((status, taskRun) => {
        taskRun.result.taskResult match {
          case TaskResult.Success(_, _) => status.copy(success = true)
          case TaskResult.Failure(_, _) => status.copy(failures = status.failures + 1)
        }
      })
      .compile
      .lastOrError
  }
}