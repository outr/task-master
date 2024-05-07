package benchmark

import cats.effect.IO
import fabric.rw.RW
import lightdb.Id
import perfolation.long2Implicits
import perfolation.numeric.Grouping
import scribe.cats.{io => logger}
import scribe.mdc.MDC
import scribe.output._
import taskmaster.task.{RunTask, Task, TaskHandler, TypedTaskResult}
import taskmaster.workflow.{WorkflowPayload, WorkflowTask, WorkflowTaskHandler}
import taskmaster.workflow.db.Workflow

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration._

object WorkflowBenchmark extends AbstractBenchmark {
  override val entries: Int = 100_000
  override val workersToCreate: Int = 100

  override protected def insertEntry(index: Int): IO[Unit] = {
    val data = WorkflowData(index)
    val t1 = WFTask1(data)
    val t2 = WFTask2(data, dependencies = Set(t1))
    val t3 = WFTask3(data, dependencies = Set(t2))
    Workflow.create(List(t1, t2, t3)).map(_ => ())
  }

  override protected val handlers: List[TaskHandler[_]] = List(
    WFTask1, WFTask2, WFTask3
  )

  val sleepTime: FiniteDuration = 100.millis

  override protected def logWaitStatus(): IO[Unit] = logger.info(out(
    "Waiting for entries to complete:",
    "\n  WFTask1: ", red(WFTask1.executions.get().f(f = 0, g = Grouping.US)),
    "\n  WFTask2: ", red(WFTask1.executions.get().f(f = 0, g = Grouping.US)),
    "\n  WFTask3: ", red(counter.get().f(f = 0, g = Grouping.US)),
    "\nof ",
    entries.f(f = 0, g = Grouping.US),
    " completed."
  ))

  case class WFTask1(runTask: RunTask[WorkflowData]) extends WorkflowTask[WorkflowData, Unit] {
    override protected def handler: WorkflowTaskHandler[WorkflowData, Unit] = WFTask1

    override def executeWithResult()(implicit mdc: MDC): IO[TypedTaskResult[Unit]] = logger.debug("Running!")
      .flatMap(_ => IO.sleep(sleepTime))
      .map { _ =>
        WFTask1.executions.incrementAndGet()
        TypedTaskResult.Success(())
      }
  }

  object WFTask1 extends WorkflowTaskHandler[WorkflowData, Unit] {
    val executions = new AtomicLong(0L)
    override implicit val rw: RW[WorkflowData] = WorkflowData.rw

    override val resultRW: RW[Unit] = implicitly

    override def create(runTask: RunTask[WorkflowData]): IO[Task[WorkflowData]] = IO(new WFTask1(runTask))
  }

  case class WFTask2(runTask: RunTask[WorkflowData]) extends WorkflowTask[WorkflowData, Unit] {
    override protected def handler: WorkflowTaskHandler[WorkflowData, Unit] = WFTask2

    override def executeWithResult()(implicit mdc: MDC): IO[TypedTaskResult[Unit]] = logger.debug("Running!")
      .flatMap(_ => IO.sleep(sleepTime))
      .map { _ =>
        WFTask2.executions.incrementAndGet()
        TypedTaskResult.Success(())
      }
  }

  object WFTask2 extends WorkflowTaskHandler[WorkflowData, Unit] {
    val executions = new AtomicLong(0L)
    override implicit val rw: RW[WorkflowData] = WorkflowData.rw

    override val resultRW: RW[Unit] = implicitly

    override def create(runTask: RunTask[WorkflowData]): IO[Task[WorkflowData]] = IO(new WFTask2(runTask))
  }

  case class WFTask3(runTask: RunTask[WorkflowData]) extends WorkflowTask[WorkflowData, Unit] {
    override protected def handler: WorkflowTaskHandler[WorkflowData, Unit] = WFTask3

    override def executeWithResult()(implicit mdc: MDC): IO[TypedTaskResult[Unit]] = logger.debug("Running!")
      .flatMap(_ => IO.sleep(sleepTime))
      .map { _ =>
        add(runTask.payload.index)
        TypedTaskResult.Success(())
      }
  }

  object WFTask3 extends WorkflowTaskHandler[WorkflowData, Unit] {
    override implicit val rw: RW[WorkflowData] = WorkflowData.rw

    override val resultRW: RW[Unit] = implicitly

    override def create(runTask: RunTask[WorkflowData]): IO[Task[WorkflowData]] = IO(new WFTask3(runTask))
  }

  case class WorkflowData(index: Int, workflowId: Id[Workflow] = Id()) extends WorkflowPayload

  object WorkflowData {
    implicit val rw: RW[WorkflowData] = RW.gen
  }
}