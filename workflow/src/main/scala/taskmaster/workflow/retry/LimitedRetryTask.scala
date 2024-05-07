package taskmaster.workflow.retry

import cats.effect.IO
import scribe.mdc.MDC
import taskmaster.task.ExecutionResult

import scala.concurrent.duration._

/**
 * LimitedRetryTask simplifies on RetryableTask by limiting the retries to a specific number
 *
 * @tparam Payload the payload that this task receives
 */
trait LimitedRetryTask[Payload] extends RetryableTask[Payload] {
  /**
   * The maximum number of times to retry
   */
  protected def maxRetries: Int

  /**
   * Receives the number of failures and should return how long to wait until the next attempt. Defaults to 5 minutes.
   */
  protected def delayRetry(failures: Int): FiniteDuration = 5.minutes

  override def retry(failures: Int, executionResult: ExecutionResult)(implicit mdc: MDC): IO[RetryResult] = if (failures <= maxRetries) {
    IO.pure(RetryResult.Schedule(delayRetry(failures)))
  } else {
    IO.pure(RetryResult.Stop)
  }
}