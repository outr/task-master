package taskmaster.task

import fabric._
import fabric.rw._

/**
 * TaskResult is the result returned from an execution of a Task
 */
sealed trait TaskResult

object TaskResult {
  private val successRW: RW[Success] = RW.gen
  private val failureRW: RW[Failure] = RW.gen
  implicit val rw: RW[TaskResult] = RW.poly[TaskResult]()(successRW, failureRW)

  /**
   * The Task successfully ran as expected
   *
   * @param result the result of the run - useful for supplying data that can be retrieved by another task in the same
   *               workflow. Defaults to Null.
   * @param continue if this is within a workflow or dependency task, it gives insight to whether to flow should
   *                 continue. Defaults to true.
   */
  case class Success(result: Json = Null, continue: Boolean = true) extends TaskResult

  /**
   * The Task failed.
   *
   * @param message a message associated with this failure
   * @param permanent true if this is a permanent failure and false if it could be retried
   */
  case class Failure(message: String, permanent: Boolean) extends TaskResult
}