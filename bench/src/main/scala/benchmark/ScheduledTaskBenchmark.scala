package benchmark

import cats.effect.IO
import fabric.rw._
import scribe.cats.{io => logger}
import scribe.mdc.MDC
import taskmaster.scheduler.Scheduler
import taskmaster.task.{RunTask, Task, TaskHandler, TaskId, TaskResult}

import java.time.Instant

object ScheduledTaskBenchmark extends AbstractBenchmark {
  override val entries: Int = 100_000
  override val workersToCreate: Int = 100

  override protected def insertEntry(index: Int): IO[Unit] =
    Scheduler.schedule(Task.applicationName, ScheduledTask.taskName, index, Instant.now(), TaskId())

  override protected val handlers: List[TaskHandler[_]] = List(ScheduledTask)

  case class ScheduledTask(runTask: RunTask[Int]) extends Task[Int] {
    override def execute()(implicit mdc: MDC): IO[TaskResult] = logger
      .trace(s"Running for ${runTask.payload}!")
      .map { _ =>
        add(runTask.payload)
        TaskResult.Success()
      }
  }

  object ScheduledTask extends TaskHandler[Int] {
    override implicit val rw: RW[Int] = intRW

    override def create(runTask: RunTask[Int]): IO[Task[Int]] = IO(ScheduledTask(runTask))
  }
}