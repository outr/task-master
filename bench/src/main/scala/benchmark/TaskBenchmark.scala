package benchmark

import cats.effect.IO
import fabric.rw._
import scribe.cats.{io => logger}
import scribe.mdc.MDC
import taskmaster.task.{RunTask, Task, TaskHandler, TaskResult}

object TaskBenchmark extends AbstractBenchmark {
  override val entries: Int = 1_000_000
  override val workersToCreate: Int = 100

  override protected def insertEntry(index: Int): IO[Unit] = SimpleTask.requestRun(caller, index)

  override protected val handlers: List[TaskHandler[_]] = List(SimpleTask)

  case class SimpleTask(runTask: RunTask[Int]) extends Task[Int] {
    override def execute()(implicit mdc: MDC): IO[TaskResult] = logger.trace(s"Running for ${runTask.payload}!")
      .map { _ =>
        add(runTask.payload)
        TaskResult.Success()
      }
  }

  object SimpleTask extends TaskHandler[Int] {
    override implicit val rw: RW[Int] = intRW

    override def create(runTask: RunTask[Int]): IO[Task[Int]] = IO(SimpleTask(runTask))
  }
}