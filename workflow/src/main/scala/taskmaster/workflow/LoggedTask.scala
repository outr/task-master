package taskmaster.workflow

import cats.effect.IO
import scribe.mdc.MDC
import taskmaster.task.{ExecutionResult, Task}
import taskmaster.workflow.db.TaskRun

/**
 * LoggedTask logs the results of a task execution to the database
 *
 * @tparam Payload the payload that this task receives
 */
trait LoggedTask[Payload] extends Task[Payload] {
  override def after(result: ExecutionResult)(implicit mdc: MDC): IO[Unit] = super.after(result).flatMap { _ =>
    TaskRun.set(TaskRun(
      id = runId,
      task = runTask.jsonTask,
      result = result
    )).map(_ => ())
  }
}