package taskmaster.messaging

/**
 * ProcessResult represents what to do with a message after processing it
 */
sealed trait ProcessResult

object ProcessResult {
  /**
   * Acknowledges the message in the queue allowing it to be disposed
   */
  case object Acknowledge extends ProcessResult

  /**
   * Rejects the message so that another worker can pick it up
   */
  case object Reject extends ProcessResult
}