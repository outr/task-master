package taskmaster.task

import cats.effect.IO
import fabric.rw._
import scribe.mdc.MDC

trait ResultingTask[Payload, Result] extends Task[Payload] {
  implicit val resultRW: RW[Result]

  override final def execute()(implicit mdc: MDC): IO[TaskResult] = executeWithResult()(mdc).map {
    case TypedTaskResult.Success(result, continue) => TaskResult.Success(result.json, continue)
    case TypedTaskResult.Failure(message, permanent) => TaskResult.Failure(message, permanent)
  }

  def executeWithResult()(implicit mdc: MDC): IO[TypedTaskResult[Result]]
}