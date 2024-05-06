package taskmaster.task

import fabric.rw._

import java.time.{Duration, Instant}

/**
 * ExecutionResult is the value passed to `Task.after` to indicate the result of executing the task.
 *
 * @param start the instant when the task was started
 * @param finished the instant when the task finished
 * @param taskResult the TaskResult returned by execute
 */
case class ExecutionResult(start: Instant, finished: Instant, taskResult: TaskResult) {
  /**
   * Elapsed provides a convenience duration to represent the time from start to finish
   */
  lazy val elapsed: Duration = Duration.between(start, finished)
}

object ExecutionResult {
  implicit val rw: RW[ExecutionResult] = RW.gen
}