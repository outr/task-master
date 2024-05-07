package benchmark

import cats.effect.unsafe.IORuntime
import cats.effect.{ExitCode, IO, IOApp}
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import perfolation.double2Implicits
import perfolation.numeric.Grouping
import profig.Profig
import scribe.{Level, Logger}
import scribe.cats.{io => logger}
import scribe.output._
import taskmaster.messaging.MessageQueue
import taskmaster.scheduler.Scheduler
import taskmaster.scheduler.db.SchedulerDB
import taskmaster.task.{Task, TaskHandler, TaskRegistry}
import taskmaster.workflow.db.WorkflowDB

import java.time.Instant
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.duration._

trait AbstractBenchmark extends IOApp {
  val entries: Int
  val workersToCreate: Int

  val insertConcurrency: Int = 32

  protected val counter = new AtomicInteger(0)
  protected val failCounter = new AtomicInteger(0)
  protected val ref = new AtomicReference[Set[Int]](Set.empty[Int])

  lazy val caller: TaskRegistry = TaskRegistry(Task.applicationName)
  lazy val workers: Vector[TaskRegistry] = (0 until workersToCreate).toVector.map(_ => createWorkers())

  override final def run(args: List[String]): IO[ExitCode] = for {
    time1 <- instant("Initializing...")
    _ <- init()
    time2 <- instant(s"Inserting ${entries.f(f = 0, g = Grouping.US)} entries...")
    _ <- insert()
    time3 <- instant("Waiting for completion...")
    _ <- IO(workers)
    _ <- waitForComplete()
    time4 <- instant("Disposing...")
    _ <- dispose()
    _ <- logger.info(out(
      s"${getClass.getSimpleName.replace("$", "")} finished with count of ",
      counter.get().f(f = 0, g = Grouping.US),
      s" (${workersToCreate.f(f = 0, g = Grouping.US)} workers)",
      s", Init Time: ${sec(time1, time2)}",
      s", Insert Time: ${sec(time2, time3)} (Concurrency of $insertConcurrency)",
      s", Process Time: ${sec(time3, time4)}",
      s" (${perSec(time3, time4, entries)}, failures: ${failCounter.get().f(f = 0, g = Grouping.US)})"
    ))
  } yield {
    ExitCode.Success
  }

  protected def initLogger(): IO[Unit] = IO {
  }

  protected def init(): IO[Unit] = for {
    _ <- initLogger()
    _ <- IO(Profig.initConfiguration())
    _ <- IO(Profig("applicationName").merge(getClass.getSimpleName.replace("$", "")))
    _ <- WorkflowDB.init()
    _ <- SchedulerDB.truncate()
    _ <- WorkflowDB.truncate()
    _ <- Scheduler.purge()
    _ <- IO(caller)
    _ = Scheduler.start()
    _ <- caller.channel.purge().flatMap { count =>
      if (count > 0) {
        logger.info(s"Purged $count messages from message queue")
      } else {
        IO.unit
      }
    }
  } yield {
    ()
  }

  protected def add(i: Int): Unit = {
    if (ref.get().contains(i)) scribe.warn(s"$i is already in the set! (${counter.get()})")
    counter.incrementAndGet()
    ref.updateAndGet(_ + i)
  }

  protected def insert(): IO[Unit] = fs2.Stream
    .fromIterator[IO]((0 until entries).iterator, 512)
    .parEvalMap(insertConcurrency) { index =>
      insertEntry(index)
    }
    .compile
    .drain

  protected def insertEntry(index: Int): IO[Unit]

  protected def hasCompleted: Boolean = counter.get() >= entries

  protected def logWaitStatus(): IO[Unit] = logger.info(out(
     "Waiting for entries to complete: ",
    red(counter.get().f(f = 0, g = Grouping.US)),
    " of ",
    entries.f(f = 0, g = Grouping.US),
    " completed."
  ))

  protected def waitForComplete(): IO[Unit] = waitFor(
    condition = hasCompleted,
    log = logWaitStatus()
  )

  def dispose(): IO[Unit] = for {
    _ <- caller.dispose()
    _ <- workers.traverse_(_.dispose())
    _ = Scheduler.stop()
    _ <- MessageQueue.dispose()
    _ <- WorkflowDB.dispose()
    _ = IORuntime.global.shutdown()
  } yield {
    ()
  }

  protected val handlers: List[TaskHandler[_]]

  private def createWorkers(): TaskRegistry = {
    val worker = TaskRegistry(Task.applicationName, registered = false)
    TaskHandler.register(
      worker, handlers: _*
    )
    worker
  }

  protected def waitFor(condition: => Boolean,
                        log: => IO[Unit] = IO.unit,
                        timeout: FiniteDuration = 30.minutes,
                        logEvery: FiniteDuration = 30.seconds,
                        start: Long = System.currentTimeMillis(),
                        lastLogged: Long = System.currentTimeMillis()): IO[Unit] = if (condition) {
    IO.unit
  } else {
    val elapsed = System.currentTimeMillis() - start
    if (elapsed > timeout.toMillis) throw new RuntimeException("Timeout!")
    IO.sleep(250.millis).flatMap { _ =>
      val now = System.currentTimeMillis()
      val (io, ll) = if (now - lastLogged > logEvery.toMillis) {
        (log, now)
      } else {
        (IO.unit, lastLogged)
      }
      io.flatMap(_ => waitFor(condition, log, timeout, logEvery, start, ll))
    }
  }

  protected def instant(description: String = ""): IO[Instant] = {
    val io = if (description.isEmpty) {
      IO.unit
    } else {
      logger.info(description)
    }
    io.flatMap(_ => IO.realTimeInstant)
  }

  protected def sec(start: Instant, end: Instant): String = {
    val elapsed = (end.toEpochMilli - start.toEpochMilli) / 1000.0
    s"${elapsed.f(f = 3, g = Grouping.US)} seconds"
  }

  protected def perSec(start: Instant, end: Instant, count: Int): String = {
    val elapsed = (end.toEpochMilli - start.toEpochMilli) / 1000.0
    val perSec = count.toDouble / elapsed
    s"${perSec.f(g = Grouping.US)}/second"
  }
}
