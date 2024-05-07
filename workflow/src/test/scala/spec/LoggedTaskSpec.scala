package spec

import cats.effect.IO
import fabric._
import fabric.rw._
import profig.Profig
import scribe.cats.{io => logger}
import scribe.mdc.MDC
import taskmaster.task.{RunTask, Task, TaskHandler, TaskRegistry, TaskResult}
import taskmaster.workflow.LoggedTask
import taskmaster.workflow.db.{TaskRun, WorkflowDB}

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.DurationInt

class LoggedTaskSpec extends Spec {
  private lazy val worker = TaskRegistry.worker("taskSpec", Task1)
  private lazy val caller = TaskRegistry("taskSpec")

  "LoggedTask" when {
    "initializing" should {
      "init configuration" in {
        Profig.initConfiguration()
        succeed
      }
      "init the database" in {
        WorkflowDB.init().map(_ => succeed)
      }
      "purge the queue" in {
        worker.channel.purge().map { i =>
          if (i != 0) scribe.warn(s"Purged $i messages")
          succeed
        }
      }
      "purge the caller registry" in {
        caller.channel.purge().map { i =>
          if (i != 0) scribe.warn(s"Purged $i messages")
          succeed
        }
      }
      "purge the database" in {
        WorkflowDB.truncate().map(_ => succeed)
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
      "verify there is a TaskRun persisted to the database" in {
        TaskRun.stream.compile.toList.map { list =>
          list.length should be(1)
          val taskRun = list.head
          taskRun.task.name should be(Task1.taskName)
          taskRun.task.payload should be(obj(
            "message" -> "Hello, World!",
            "fail" -> false,
            "throwException" -> false
          ))
          taskRun.result.taskResult should be(TaskResult.Success())
        }
      }
      "purge the record from the database" in {
        TaskRun.truncate().map(_ => succeed)
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
      "verify there is a failed TaskRun persisted to the database" in {
        TaskRun.stream.compile.toList.map { list =>
          list.length should be(1)
          val taskRun = list.head
          taskRun.task.name should be(Task1.taskName)
          taskRun.task.payload should be(obj(
            "message" -> "This is a failure!",
            "fail" -> true,
            "throwException" -> false
          ))
          taskRun.result.taskResult should be(TaskResult.Failure("This is a failure!", permanent = true))
        }
      }
      "purge the record from the database" in {
        TaskRun.truncate().map(_ => succeed)
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
      "verify there is a failed TaskRun persisted to the database" in {
        TaskRun.stream.compile.toList.map { list =>
          list.length should be(1)
          val taskRun = list.head
          taskRun.task.name should be(Task1.taskName)
          taskRun.task.payload should be(obj(
            "message" -> "This is another failure!",
            "fail" -> true,
            "throwException" -> true
          ))
          taskRun.result.taskResult should be(TaskResult.Failure("This is another failure!", permanent = false))
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

  case class Task1(runTask: RunTask[Task1Payload]) extends LoggedTask[Task1Payload] {
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