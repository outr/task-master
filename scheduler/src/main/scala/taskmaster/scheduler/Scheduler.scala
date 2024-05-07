package taskmaster.scheduler

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import fabric.Json
import fabric.rw._
import lightdb.query.Sort
import perfolation._
import profig.Profig
import scribe.cats.{io => logger}
import taskmaster.messaging.{ChannelSubscription, MessageQueue, ProcessResult}
import taskmaster.scheduler.db.ScheduledEvent
import taskmaster.task.{TaskId, TaskRegistry}

import java.time.Instant
import java.util.concurrent.{Executors, ThreadFactory}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationLong

// TODO: Make sure that Scheduler never dies on run

/**
 * Scheduler provides the capabilities for both scheduling tasks to be executed in the future as well as monitoring that
 * schedule.
 *
 * To schedule a task, simply use the `schedule` method.
 *
 * To start the scheduler to monitor the schedule and kick off work events, call the `start` method.
 */
class Scheduler {
  private lazy val config = Profig("scheduler").as[SchedulerConfig]
  private lazy val channel = MessageQueue.channel[String]("scheduleChanged")
  private lazy val changed = new AtomicBoolean(false)

  private var subscription: Option[ChannelSubscription] = None
  private var keepAlive = true

  private lazy val executionContext: ExecutionContext = {
    val threadFactory = new ThreadFactory {
      private val counter = new AtomicLong(0L)

      override def newThread(r: Runnable): Thread = {
        val t = new Thread(r)
        t.setName(s"task-master-${counter.incrementAndGet()}")
        t.setDaemon(true)
        t
      }
    }
    val executorService = config.fixedThreadCount match {
      case Some(threadCount) => Executors.newFixedThreadPool(threadCount, threadFactory)
      case None => Executors.newCachedThreadPool(threadFactory)
    }
    ExecutionContext.fromExecutor(executorService)
  }
  private lazy val (scheduler, shutdownScheduler) = IORuntime.createDefaultScheduler("task-master")
  private implicit lazy val ioRuntime: IORuntime = IORuntime.builder()
    .setCompute(executionContext, () => ())
    .setBlocking(executionContext, () => ())
    .setScheduler(scheduler, shutdownScheduler)
    .build()

  /**
   * Schedules a task to be run in the future
   *
   * @param applicationName the application name associated with this task
   * @param taskName        the task's name
   * @param payload         the payload for this execution
   * @param when            the instant this should be run (Note: if scheduled in the past it will run immediately)
   * @param taskId          the TaskId for this task
   */
  def schedule[Payload: RW](applicationName: String,
                                      taskName: String,
                                      payload: Payload,
                                      when: Instant,
                                      taskId: TaskId): IO[Unit] = {
    val delay = ((when.toEpochMilli - System.currentTimeMillis()) / 1000.0).f()
    scribe.debug(s"Scheduling $taskName to run in $delay seconds...")
    ScheduledEvent.set(ScheduledEvent(
        applicationName = applicationName,
        taskName = taskName,
        payload = payload.json,
        scheduled = when,
        taskId = taskId
      ))
      .flatMap { _ =>
        channel.publish(taskName)
      }
  }

  /**
   * Starts the scheduler monitor to pick up scheduled tasks and fire events for workers to run
   */
  def start(): Unit = synchronized {
    val subscription = channel.subscribe { taskName =>
      for {
        _ <- logger.debug(s"ScheduleChanged with addition of $taskName")
        _ <- channel.purge()
        _ = changed.set(true)
      } yield {
        ProcessResult.Acknowledge
      }
    }
    this.subscription = Some(subscription)
    keepAlive = true
    changed.set(true)
    run(Nil).unsafeRunAndForget()
  }

  /**
   * Stops the running scheduler if running
   */
  def stop(): Unit = {
    keepAlive = false
    subscription.foreach { sub =>
      channel.unsubscribe(sub)
    }
  }

  private def run(events: List[ScheduledEvent]): IO[Unit] = if (changed.compareAndSet(true, false)) {
    scribe.debug("Refreshing schedule!")
    scheduled().flatMap { list =>
      scribe.debug(s"New schedule size: ${list.size}")
      run(list)
    }
  } else if (events.isEmpty) {
    scribe.debug("No events, sleeping...")
    IO.sleep(config.delay).flatMap(_ => run(events))
  } else {
    val now = System.currentTimeMillis()
    val readyEvents = events.takeWhile(_.scheduled.toEpochMilli - now <= 0)
    if (readyEvents.isEmpty) {
      val event = events.head
      val nextRun = event.scheduled.toEpochMilli - now
      val sleepTime = if (nextRun < config.delay.toMillis) {
        nextRun.millis
      } else {
        config.delay
      }
      IO.sleep(sleepTime).flatMap(_ => run(events))
    } else {
      scribe.info(s"Running scheduled ${readyEvents.length} event(s) now...")
      for {
        // Submit requests to run tasks
        _ <- readyEvents.parTraverse_ { event =>
          TaskRegistry(event.applicationName).requestRun[Json](event.taskName, event.payload, event.taskId)
        }
        // Delete ScheduledEvents
        _ <- readyEvents.map(se => ScheduledEvent.delete(se._id)).sequence
        // Filter out the events we scheduled
        updatedEvents <- IO(events.filterNot(readyEvents.contains))
        _ <- if (updatedEvents.length != events.length - readyEvents.length) {
          logger.error(s"UpdatedEvents: ${updatedEvents.length} was not equal to ${events.length - readyEvents.length} (Events: ${events.length} - ReadyEvents: ${readyEvents.length})")
        } else {
          IO.unit
        }
        // Update the scheduled list if we've run out of scheduled items to make sure we're out
        _ = if (updatedEvents.isEmpty) changed.set(true)
        // Recurse
        _ <- run(updatedEvents)
      } yield {
        ()
      }
    }
  }

  /**
   * Retrieves the scheduled events sorted by scheduled ascending
   *
   * @param limit the limit to the number of events to return (Defaults to scheduler.maxQueueSize, 100)
   * @return List[ScheduledEvent]
   */
  def scheduled(limit: Int = config.maxQueueSizeInMemory): IO[List[ScheduledEvent]] = ScheduledEvent.query
    .sort(Sort.ByField(ScheduledEvent.scheduled))
    .pageSize(limit)
    .toList

  def purge(): IO[Int] = channel.purge()

  /**
   * Disposes the channel and cleans up
   */
  def dispose(): IO[Unit] = for {
    _ <- channel.dispose()
  } yield {
    ()
  }
}

object Scheduler extends Scheduler