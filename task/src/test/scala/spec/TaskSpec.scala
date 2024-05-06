package spec

import cats.effect.IO
import fabric.rw._
import profig.Profig
import scribe.cats.{io => logger}
import scribe.mdc.MDC
import taskmaster.task.{RunTask, Task, TaskHandler, TaskRegistry, TaskResult}

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.DurationInt

class TaskSpec extends Spec {
  private lazy val worker = TaskRegistry.worker("taskSpec", Task1)
  private lazy val caller = TaskRegistry("taskSpec")

  "Task" when {
    "initializing" should {
      "init configuration" in {
        Profig.initConfiguration()
        succeed
      }
      "purge the queue" in {
        worker.channel.purge().map { i =>
          if (i != 0) scribe.warn(s"Purged $i messages")
          succeed
        }
      }
      "validate worker" in {
        worker.registered should be(1)
      }
    }
    "validating a successful run" should {
      "verify test1 hasn't already been run" in {
        Task1.executions.get() should be(0L)
      }
      "run test1 task" in {
        Task1.requestRun(caller, Task1Payload("Hello, World!")).map { _ =>
          succeed
        }
      }
      "wait for test1 to run successfully" in {
        waitFor(Task1.executions.get(), 1L).map { _ =>
          Task1.executions.get() should be(1L)
        }
      }
    }
    "validating a handled failure" should {
      "run test1" in {
        caller.requestRun(Task1.taskName, Task1Payload("This is a failure!", fail = true)).map { _ =>
          succeed
        }
      }
      "wait for test1 to do its run successfully" in {
        waitFor(Task1.executions.get(), 2).map { _ =>
          Task1.executions.get() should be(2L)
        }
      }
    }
    "validating an unhandled failure" should {
      "run test1" in {
        caller.requestRun(Task1.taskName, Task1Payload("This is another failure!", fail = true, throwException = true)).map { _ =>
          succeed
        }
      }
      "wait for test1 to run successfully" in {
        waitFor(Task1.executions.get(), 3L).map { _ =>
          Task1.executions.get() should be(3L)
        }
      }
    }
    "cleanup" should {
      "dispose" in {
        for {
          _ <- worker.dispose()
          _ <- caller.dispose()
        } yield {
          succeed
        }
      }
    }
  }

  case class Task1(runTask: RunTask[Task1Payload]) extends Task[Task1Payload] {
    override def execute()(implicit mdc: MDC): IO[TaskResult] = {
      mdc("payload") = runTask.payload
      for {
        _ <- logger.info(s"Executing Task1 with message: ${runTask.payload.message}. Sleeping...")
        _ <- IO.sleep(1.second)
        _ <- logger.info("Task1 finishing!")
      } yield {
        Task1.executions.incrementAndGet()
        if (runTask.payload.fail) {
          if (runTask.payload.throwException) throw new RuntimeException(runTask.payload.message)
          TaskResult.Failure(runTask.payload.message, permanent = true)
        } else {
          TaskResult.Success()
        }
      }
    }
  }

  object Task1 extends TaskHandler[Task1Payload] {
    val executions = new AtomicLong(0L)

    override implicit val rw: RW[Task1Payload] = Task1Payload.rw

    override def create(runTask: RunTask[Task1Payload]): IO[Task[Task1Payload]] = IO {
      new Task1(runTask)
    }
  }

  case class Task1Payload(message: String, fail: Boolean = false, throwException: Boolean = false)

  object Task1Payload {
    implicit val rw: RW[Task1Payload] = RW.gen
  }
}