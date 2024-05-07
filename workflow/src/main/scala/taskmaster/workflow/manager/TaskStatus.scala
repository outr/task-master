package taskmaster.workflow.manager

import fabric.rw._

/**
 * TaskStatus is returned from TaskManager to represent the status of runs of LoggedTask instances in the database.
 *
 * @param success true if it has ever run successfully
 * @param failures the number of failures recorded
 */
case class TaskStatus(success: Boolean, failures: Int) {
  /**
   * True if a success or failure has occurred for this task
   */
  def exists: Boolean = success || failures > 0
}

object TaskStatus {
  implicit val rw: RW[TaskStatus] = RW.gen
}