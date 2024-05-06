package taskmaster.task

import cats.effect.IO
import lightdb.Unique
import profig.Profig
import scribe.mdc.MDC

/**
 * Task is the core representation of a unit of work. There are several mix-in traits to expand this functionality, but
 * just extending from this trait is the only necessity to be an executable task.
 *
 * @tparam Payload the payload that this task receives
 */
trait Task[Payload] {
  /**
   * A unique identifier for this run. TaskId is unique across executions of the same task with the same payload, but
   * runId is unique for each run.
   */
  lazy val runId: String = Unique()

  /**
   * The RunTask associated with this Task
   */
  def runTask: RunTask[Payload]

  /**
   * Convenience method to get the payload
   */
  def payload: Payload = runTask.payload

  /**
   * Called before execute
   */
  def before()(implicit mdc: MDC): IO[Unit] = IO.unit

  /**
   * The main execution block of a Task. The responsibility for this method is to do the work of this task and then
   * return a TaskResult.
   */
  def execute()(implicit mdc: MDC): IO[TaskResult]

  /**
   * Called after execution of a Task to support handling of success and failures to run.
   */
  def after(result: ExecutionResult)(implicit mdc: MDC): IO[Unit] = IO.unit

  /**
   * When `execute` throws an unhandled exception, this method is invoked to determine if it is a permanent failure in
   * order to derive the ExecutionResult. Defaults to false.
   */
  def isPermanent(throwable: Throwable): Boolean = false
}

object Task {
  /**
   * The application name is utilized to differentiate between messaging queues in order to only receive work that can
   * be handled in that queue. This should be unique between applications to avoid receiving another application's work.
   */
  def applicationName: String = Profig("applicationName")
    .asOr[String](throw new RuntimeException("applicationName not specified"))
}