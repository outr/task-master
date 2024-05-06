package taskmaster.scheduler

import fabric.rw.RW

import scala.concurrent.duration._

/**
 * Scheduler configuration
 *
 * @param delay the time the scheduler sleeps between updates when monitoring for scheduled tasks to run (defaults to 5seconds)
 * @param maxQueueSizeInMemory the maximum queue size to keep in memory (defaults to 1000)
 */
case class SchedulerConfig(delay: FiniteDuration = 5.seconds,
                           maxQueueSizeInMemory: Int = 5000)

object SchedulerConfig {
  implicit val rw: RW[SchedulerConfig] = RW.gen
}