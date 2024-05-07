package benchmark

import cats.effect.IO
import fabric.rw._
import scribe.cats.{io => logger}
import scribe.mdc.MDC
import taskmaster.task.{ExecutionResult, RunTask, Task, TaskHandler, TaskResult}
import taskmaster.workflow.retry.{RetryResult, RetryableTask}

import scala.concurrent.duration._
import scala.util.Random

object FailingTaskBenchmark extends AbstractBenchmark {
  override val entries: Int = 100_000
  override val workersToCreate: Int = 100

  override protected def insertEntry(index: Int): IO[Unit] = MayFailTask.requestRun(caller, index)

  override protected val handlers: List[TaskHandler[_]] = List(MayFailTask)

  case class MayFailTask(runTask: RunTask[Int]) extends RetryableTask[Int] {
    override def execute()(implicit mdc: MDC): IO[TaskResult] = logger.trace(s"Running for ${runTask.payload}!")
      .map { _ =>
        if (Random.nextBoolean()) {
          add(runTask.payload)
          TaskResult.Success()
        } else {
          failCounter.incrementAndGet()
          TaskResult.Failure("Luck of the draw!", permanent = false)
        }
      }

    override def retry(failures: Int, executionResult: ExecutionResult)(implicit mdc: MDC): IO[RetryResult] = IO.pure(RetryResult.Schedule(0.seconds))
  }

  object MayFailTask extends TaskHandler[Int] {
    override implicit val rw: RW[Int] = intRW

    override def create(runTask: RunTask[Int]): IO[Task[Int]] = IO(MayFailTask(runTask))
  }
}