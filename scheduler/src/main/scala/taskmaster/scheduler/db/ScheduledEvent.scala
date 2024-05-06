package taskmaster.scheduler.db

import fabric.Json
import fabric.rw._
import lightdb.model.RecordDocumentCollection
import lightdb.sqlite.{SQLIndexedField, SQLiteSupport}
import lightdb.{Id, RecordDocument}
import taskmaster.task.{TaskId, instantRW}

import java.time.Instant

/**
 * ScheduledEvent is utilized by Scheduler to persist and retrieve scheduled tasks to be run
 *
 * @param applicationName the application name for the scheduled task
 * @param taskName the name of the task
 * @param payload the payload to be supplied to the task
 * @param scheduled the instant when it scheduled to execute
 * @param taskId the TaskId for this task
 * @param _id the unique identifier
 */
case class ScheduledEvent(applicationName: String,
                          taskName: String,
                          payload: Json,
                          scheduled: Instant,
                          taskId: TaskId,
                          created: Long = System.currentTimeMillis(),
                          modified: Long = System.currentTimeMillis(),
                          _id: Id[ScheduledEvent] = Id()) extends RecordDocument[ScheduledEvent]

object ScheduledEvent extends RecordDocumentCollection[ScheduledEvent]("scheduledEvents", SchedulerDB) with SQLiteSupport[ScheduledEvent] {
  override implicit val rw: RW[ScheduledEvent] = RW.gen

  override val autoCommit: Boolean = true

  val scheduled: SQLIndexedField[Instant, ScheduledEvent] = index("scheduled", doc => Some(doc.scheduled))
}