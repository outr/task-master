package taskmaster.task

import fabric.rw._
import lightdb.Unique

/**
 * Represents a unique identifier persistent across multiple retries of the same task.
 */
case class TaskId(value: String = Unique()) extends AnyVal

object TaskId {
  implicit val rw: RW[TaskId] = RW.string(_.value, s => TaskId(s))
}