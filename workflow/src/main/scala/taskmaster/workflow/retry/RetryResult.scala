package taskmaster.workflow.retry

import scala.concurrent.duration.FiniteDuration

/**
 * RetryResult is used by RetryableTask to determine if and how a task should be retried
 */
sealed trait RetryResult

object RetryResult {
  /**
   * Schedules a retry to run at `when` in the future
   */
  case class Schedule(when: FiniteDuration) extends RetryResult

  /**
   * Stops retrying this task
   */
  case object Stop extends RetryResult
}