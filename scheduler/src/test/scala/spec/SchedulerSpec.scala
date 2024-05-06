package spec

import cats.effect.IO
import fabric._
import fabric.rw.RW
import profig.Profig
import scribe.cats.{io => logger}
import scribe.mdc.MDC
import taskmaster.scheduler.Scheduler
import taskmaster.scheduler.db.SchedulerDB
import taskmaster.task._

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration._

class SchedulerSpec extends Spec {
  private lazy val worker = TaskRegistry(Task.applicationName)

  "Scheduler" when {
    "initializing" should {
      "init configuration" in {
        Profig.initConfiguration()
        Profig("applicationName").merge("schedulerSpec")
        succeed
      }
      "init the database" in {
        SchedulerDB.init().map(_ => succeed)
      }
      "verify the applicationName is correct" in {
        Task.applicationName should be("schedulerSpec")
        worker.applicationName should be("schedulerSpec")
      }
      "truncate the scheduling queue" in {
        SchedulerDB.truncate().map(_ => succeed)
      }
      "purge the scheduler queue" in {
        Scheduler.purge().map(_ => succeed)
      }
      "register a task" in {
        worker.register[MyPayload](ScheduledTask1.name) { runTask =>
          IO(new ScheduledTask1(runTask))
        }
        succeed
      }
    }
    "scheduling a task" should {
      "schedule a task to run in the future" in {
        Scheduler.schedule[MyPayload](
          applicationName = Task.applicationName,
          taskName = ScheduledTask1.name,
          payload = MyPayload("Scheduled 1"),
          when = Instant.now().plusSeconds(1),
          taskId = TaskId("myPayload")
        )
      }
      "verify the task hasn't already run" in {
        ScheduledTask1.executions.get() should be(0L)
      }
      "wait for the task to run" in {
        Scheduler.start()
        waitFor(ScheduledTask1.executions.get(), 1L).map {_ =>
          Scheduler.stop()
          succeed
        }
      }
    }
    "schedule two tasks with an immediate run and a future run" should {
      "schedule the first task for later" in {
        Scheduler.schedule[MyPayload](Task.applicationName, ScheduledTask1.name, MyPayload("Scheduled 2"), Instant.now().plusSeconds(100), TaskId("scheduled2"))
      }
      "schedule the second task for the past" in {
        Scheduler.schedule[MyPayload](Task.applicationName, ScheduledTask1.name, MyPayload("Scheduled 3"), Instant.now().minusSeconds(100), TaskId("scheduled3"))
      }
      "wait for the task to run" in {
        Scheduler.start()
        IO.sleep(2.seconds).flatMap { _ =>
          Scheduler.stop()
          Scheduler.scheduled()
        }.map { scheduled =>
          ScheduledTask1.executions.get() should be(2L)
          scheduled.length should be(1)
          scheduled.head.payload should be(obj(
            "message" -> "Scheduled 2"
          ))
        }
      }
    }
    "cleanup" should {
      "dispose" in {
        for {
          _ <- worker.dispose()
        } yield {
          succeed
        }
      }
    }
  }

  case class ScheduledTask1(runTask: RunTask[MyPayload]) extends Task[MyPayload] {
    override def execute()(implicit mdc: MDC): IO[TaskResult] = {
      mdc("payload") = runTask.payload
      for {
        _ <- logger.info(s"Executing ScheduledTask1 with message: ${runTask.payload.message}. Sleeping...")
        _ <- IO.sleep(1.second)
        _ <- logger.info("ScheduledTask1 finishing!")
      } yield {
        ScheduledTask1.executions.incrementAndGet()
        TaskResult.Success()
      }
    }
  }

  object ScheduledTask1 {
    val name: String = "scheduledTask"

    val executions = new AtomicLong(0L)
  }

  case class MyPayload(message: String)

  object MyPayload {
    implicit val rw: RW[MyPayload] = RW.gen
  }
}
