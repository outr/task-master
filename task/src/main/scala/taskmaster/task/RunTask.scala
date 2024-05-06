package taskmaster.task

import fabric.Json
import fabric.rw._

/**
 * RunTask is received by a Task to include information for its runtime information.
 *
 * @param name the name of the task
 * @param taskId the TaskId associated with the task
 * @param payload the payload data
 */
case class RunTask[Payload](name: String, taskId: TaskId, payload: Payload)
                           (implicit val rw: RW[Payload]) {
  def jsonTask: RunTask[Json] = copy[Json](payload = payload.json)
}

object RunTask {
  implicit def rw[Payload: RW]: RW[RunTask[Payload]] = RW.gen
}