package spec

import cats.effect.IO
import fabric.rw._
import lightdb.Id
import profig.Profig
import scribe.cats.{io => logger}
import scribe.mdc.MDC
import taskmaster.scheduler._
import taskmaster.scheduler.db.SchedulerDB
import taskmaster.task._
import taskmaster.workflow.{WorkflowPayload, WorkflowTask, WorkflowTaskHandler}
import taskmaster.workflow.db.{Workflow, WorkflowDB}

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.DurationInt

class WorkflowSpec extends Spec {
  private lazy val scheduler = new Scheduler
  private lazy val worker = TaskRegistry(Task.applicationName)
  private lazy val worker2 = TaskRegistry(Task.applicationName, registered = false)

  "Workflow" when {
    "initializing" should {
      "init configuration" in {
        Profig.initConfiguration()
        Profig("applicationName").merge("workflowSpec")
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
      "assert the applicationName is correct" in {
        Task.applicationName should be("workflowSpec")
        worker.applicationName should be("workflowSpec")
        worker2.applicationName should be("workflowSpec")
      }
      "start the Scheduler" in {
        scheduler.start()
        succeed
      }
    }
    "configuring a workflow" should {
      "register a workflow to be run" in {
        TaskHandler.register(
          worker, WFTask1, WFTask2, WFTask3
        )
        succeed
      }
      "register a second workflow to be run" in {
        TaskHandler.register(
          worker2, WFTask1, WFTask2, WFTask3
        )
        succeed
      }
      "create and schedule a workflow" in {
        val data = WorkflowData()
        val t1 = WFTask1(data)
        val t2 = WFTask2(data, dependencies = Set(t1))
        val t3 = WFTask3(data, dependencies = Set(t2))

        Workflow.create(List(t1, t2, t3)).map { workflow =>
          workflow.queue.map(_.name).toSet should be(Set(WFTask2.taskName, WFTask3.taskName))
          workflow.scheduled.map(_.name) should be(List(WFTask1.taskName))
          workflow.completed.length should be(0)
        }
      }
      "wait for first task to complete" in {
        waitFor(WFTask1.executions.get(), 1).map { _ =>
          WFTask2.executions.get() should be(0)
          WFTask3.executions.get() should be(0)
        }
      }
      "wait for second task to complete" in {
        waitFor(WFTask2.executions.get(), 1).map { _ =>
          WFTask3.executions.get() should be(0)
        }
      }
      "wait for third task to complete" in {
        waitFor(WFTask3.executions.get(), 1).map(_ => succeed)
      }
    }
    "cleanup" should {
      "dispose" in {
        for {
          _ <- worker.dispose()
          _ <- worker2.dispose()
          _ = scheduler.stop()
        } yield {
          succeed
        }
      }
    }
  }

  case class WFTask1(runTask: RunTask[WorkflowData]) extends WorkflowTask[WorkflowData, WorkflowData] {
    override protected def handler: WorkflowTaskHandler[WorkflowData, WorkflowData] = WFTask1

    override def executeWithResult()(implicit mdc: MDC): IO[TypedTaskResult[WorkflowData]] = logger.info("Running!")
      .flatMap(_ => IO.sleep(1.seconds))
      .map { _ =>
        WFTask1.executions.incrementAndGet()
        TypedTaskResult.Success(payload.copy(tasksRun = payload.tasksRun + runTask.name))
      }
  }

  object WFTask1 extends WorkflowTaskHandler[WorkflowData, WorkflowData] {
    val executions = new AtomicLong(0L)

    override implicit val rw: RW[WorkflowData] = WorkflowData.rw
    override val resultRW: RW[WorkflowData] = WorkflowData.rw

    override def create(runTask: RunTask[WorkflowData]): IO[Task[WorkflowData]] = IO(new WFTask1(runTask))
  }

  case class WFTask2(runTask: RunTask[WorkflowData]) extends WorkflowTask[WorkflowData, WorkflowData] {
    override protected def handler: WorkflowTaskHandler[WorkflowData, WorkflowData] = WFTask2

    override def executeWithResult()(implicit mdc: MDC): IO[TypedTaskResult[WorkflowData]] = logger.info("Running!")
      .flatMap(_ => IO.sleep(1.seconds))
      .map { _ =>
        WFTask2.executions.incrementAndGet()
        TypedTaskResult.Success(payload.copy(tasksRun = payload.tasksRun + runTask.name))
      }
  }

  object WFTask2 extends WorkflowTaskHandler[WorkflowData, WorkflowData] {
    val executions = new AtomicLong(0L)

    override implicit val rw: RW[WorkflowData] = WorkflowData.rw
    override val resultRW: RW[WorkflowData] = WorkflowData.rw

    override def create(runTask: RunTask[WorkflowData]): IO[Task[WorkflowData]] = IO(new WFTask2(runTask))
  }

  case class WFTask3(runTask: RunTask[WorkflowData]) extends WorkflowTask[WorkflowData, WorkflowData] {
    override protected def handler: WorkflowTaskHandler[WorkflowData, WorkflowData] = WFTask3

    override def executeWithResult()(implicit mdc: MDC): IO[TypedTaskResult[WorkflowData]] = {
      dependency(WFTask2).flatMap { data =>
        val tasksRun = data.tasksRun
        logger.warn(s"Running! WFTask2: $tasksRun")
      }.flatMap(_ => IO.sleep(1.seconds))
        .map { _ =>
          WFTask3.executions.incrementAndGet()
          TypedTaskResult.Success(payload.copy(tasksRun = payload.tasksRun + runTask.name))
        }
    }
  }

  object WFTask3 extends WorkflowTaskHandler[WorkflowData, WorkflowData] {
    val executions = new AtomicLong(0L)

    override implicit val rw: RW[WorkflowData] = WorkflowData.rw
    override val resultRW: RW[WorkflowData] = WorkflowData.rw

    override def create(runTask: RunTask[WorkflowData]): IO[Task[WorkflowData]] = IO(new WFTask3(runTask))
  }

  case class WorkflowData(workflowId: Id[Workflow] = Id(),
                          tasksRun: Set[String] = Set.empty) extends WorkflowPayload

  object WorkflowData {
    implicit val rw: RW[WorkflowData] = RW.gen
  }
}
