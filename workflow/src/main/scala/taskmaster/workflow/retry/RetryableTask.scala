package taskmaster.workflow.retry

import cats.effect.IO
import scribe.cats.{io => logger}
import scribe.mdc.MDC
import taskmaster.scheduler.Scheduler
import taskmaster.task.{ExecutionResult, Task, TaskResult}
import taskmaster.workflow.LoggedTask
import taskmaster.workflow.manager.TaskManager

import java.time.Instant

/**
 * RetryableTask overloads the after method to handle non-permanent TaskResult.Failures and calls `retry` to allow the
 * retrying of the task.
 *
 * Note: this extends from LoggedTask because that is the only way we can know how many failures have occurred.
 *
 * @tparam Payload the payload that this task receives
 */
trait RetryableTask[Payload] extends LoggedTask[Payload] {
  override def after(er: ExecutionResult)(implicit mdc: MDC): IO[Unit] = er.taskResult match {
    case _: TaskResult.Success => super.after(er)
    case TaskResult.Failure(message, permanent) if permanent => super.after(er).flatMap { _ =>
      logger.error(s"Permanent failure: $message")
    }
    case TaskResult.Failure(_, _) => for {
      _ <- super.after(er)
      status <- TaskManager.status(runTask.taskId)
      retryResult <- retry(status.failures, er)
      _ <- retryResult match {
        case RetryResult.Schedule(when) => Scheduler.schedule(
          applicationName = Task.applicationName,
          taskName = runTask.name,
          payload = runTask.payload,
          when = Instant.ofEpochMilli(System.currentTimeMillis() + when.toMillis),
          taskId = runTask.taskId
        )(runTask.rw)
        case RetryResult.Stop => logger.warn(s"Task ${runTask.name} stopped")
      }
    } yield {
      ()
    }
  }

  /**
   * Receives the number of failures that have occurred along with the most recent ExecutionResult.
   *
   * @param failures the number of time this task has failed
   * @param executionResult the ExecutionResult for the latest run
   * @return IO[RetryResult]
   */
  def retry(failures: Int, executionResult: ExecutionResult)(implicit mdc: MDC): IO[RetryResult]
}