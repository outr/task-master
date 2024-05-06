package taskmaster.task

import cats.effect.IO
import fabric.Json
import fabric.rw.RW
import scribe.cats.{io => logger}
import scribe.mdc.MDC
import taskmaster.messaging.ProcessResult

/**
 * TaskRunner is a helper class to execute a task and properly handle the steps in that execution.
 *
 * @param name the name of the task
 * @param create the task creation function
 * @param logCompletion if true, will log the completion of the task (Defaults to false)
 */
case class TaskRunner[Payload](name: String,
                               create: RunTask[Payload] => IO[Task[Payload]],
                               logCompletion: Boolean = false)(implicit val rw: RW[Payload]) {
  def runWithJson(runTask: RunTask[Json]): IO[ProcessResult] = IO(
    runTask.copy[Payload](payload = rw.write(runTask.payload))
  ).flatMap(run)

  def run(runTask: RunTask[Payload]): IO[ProcessResult] = MDC { implicit mdc =>
    for {
      task <- create(runTask)
      _ = mdc("task") = name
      _ = mdc("taskId") = task.runId
      start <- IO.realTimeInstant
      _ <- task.before()
      result <- task.execute().attempt
      _ <- if (logCompletion) {
        logger.info(s"Task execution completed ${if (result.isRight) "successfully" else "with failure"}.")
      } else {
        IO.unit
      }
      finished <- IO.realTimeInstant
      executionResult = result match {
        case Left(t) => ExecutionResult(start, finished, TaskResult.Failure(t.getMessage, task.isPermanent(t)))
        case Right(taskResult) => ExecutionResult(start, finished, taskResult)
      }
      _ <- task.after(executionResult)
    } yield {
      ProcessResult.Acknowledge
    }
  }
}
