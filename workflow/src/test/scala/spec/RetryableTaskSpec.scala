package spec

import cats.effect.IO
import profig.Profig
import scribe.cats.{io => logger}
import scribe.mdc.MDC
import taskmaster.scheduler._
import taskmaster.scheduler.db.SchedulerDB
import taskmaster.task._
import taskmaster.workflow.LoggedTask
import taskmaster.workflow.db.WorkflowDB
import taskmaster.workflow.retry.{RetryResult, RetryableTask}

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration._

class RetryableTaskSpec extends Spec {
  private lazy val scheduler = new Scheduler
  private lazy val worker = TaskRegistry(Task.applicationName)

  "RetryableTask" when {
    "initializing" should {
      "init configuration" in {
        Profig.initConfiguration()
        Profig("applicationName").merge("retryableTaskSpec")
        succeed
      }
      "init the database" in {
        WorkflowDB.init().map(_ => succeed)
      }
      "truncate the database" in {
        SchedulerDB.truncate().flatMap { _ =>
          WorkflowDB.truncate().map(_ => succeed)
        }
      }
      "purge the scheduler queue" in {
        scheduler.purge().map(_ => succeed)
      }
      "register a task" in {
        worker.register[String](RetryTask1.name) { runTask =>
          IO(new RetryTask1(runTask))
        }
        succeed
      }
      "start the Scheduler" in {
        scheduler.start()
        succeed
      }
    }
    "attempting retries and failures" should {
      "submit the task to started" in {
        scheduler.schedule(Task.applicationName, RetryTask1.name, "Retry testing!", Instant.now(), TaskId()).map { _ =>
          succeed
        }
      }
      "wait for 5 failures" in {
        waitFor(RetryTask1.stopped, true, 10.seconds).map { _ =>
          succeed
        }
      }
    }
    "cleanup" should {
      "dispose" in {
        for {
          _ <- worker.dispose()
          _ = scheduler.stop()
        } yield {
          succeed
        }
      }
    }
  }

  case class RetryTask1(runTask: RunTask[String]) extends RetryableTask[String] with LoggedTask[String] {
    override def retry(failures: Int, executionResult: ExecutionResult)
                      (implicit mdc: MDC): IO[RetryResult] = {
      logger.error(s"Evaluating retry (failures: $failures, result: $executionResult)").map { _ =>
        if (failures < 5) {
          RetryResult.Schedule(0.millis)
        } else {
          RetryTask1.stopped = true
          RetryResult.Stop
        }
      }
    }

    override def execute()(implicit mdc: MDC): IO[TaskResult] = {
      mdc("payload") = runTask.payload
      logger.info(s"Executing task! ${runTask.payload}").flatMap { _ =>
        throw new RuntimeException("Failure!")
      }
    }
  }

  object RetryTask1 {
    val name: String = "retryTask1"
    var stopped: Boolean = false
  }
}
