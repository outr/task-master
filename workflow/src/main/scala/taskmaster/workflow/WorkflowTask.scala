package taskmaster.workflow

import cats.effect.IO
import fabric.rw.{Asable, RW}
import scribe.cats.{io => logger}
import scribe.mdc.MDC
import taskmaster.task.{ExecutionResult, ResultingTask, Task, TaskId, TaskResult}
import taskmaster.workflow.db.{Workflow, WorkflowDB}

/**
 * WorkflowTask represents a task within a Workflow and manages completion and triggering of additional tasks in the
 * workflow as dependencies are resolved.
 *
 * @tparam Payload the payload that this task receives
 */
trait WorkflowTask[Payload <: WorkflowPayload, Result] extends ResultingTask[Payload, Result] with LoggedTask[Payload] {
  protected def handler: WorkflowTaskHandler[Payload, Result]
  protected def workflow: IO[Workflow] = Workflow(payload.workflowId)
  protected def resultFor[P <: WorkflowPayload, R](taskId: TaskId, handler: WorkflowTaskHandler[P, R]): IO[R] =
    workflow.map { w =>
      w.completed
        .find(_.taskId == taskId)
        .getOrElse(throw new RuntimeException(s"Unable to find $taskId in ${w.completed}"))
        .result
        .map(_.as[R](handler.resultRW))
        .getOrElse(throw new RuntimeException(s"No result defined for ${handler.taskName} ($taskId)"))
    }

  protected def dependencies[P <: WorkflowPayload, R](handler: WorkflowTaskHandler[P, R]): IO[List[R]] =
    workflow.map { w =>
      val taskInstance = w.taskInstance(runTask.taskId)
        .getOrElse(throw new RuntimeException("Unable to find TaskInstance in Workflow!"))
      taskInstance.dependsOn.toList.flatMap { taskId =>
        w.completed.find(_.taskId == taskId)
      }.collect {
        case taskInstance if taskInstance.name == handler.taskName =>
          taskInstance.result.map(_.as[R](handler.resultRW))
      }.flatten
    }

  protected def dependency[P <: WorkflowPayload, R](handler: WorkflowTaskHandler[P, R]): IO[R] =
    dependencies[P, R](handler)
      .map(_.headOption.getOrElse(throw new RuntimeException(s"No dependency found for ${handler.taskName}")))

  override implicit val resultRW: RW[Result] = handler.resultRW

  override def after(er: ExecutionResult)(implicit mdc: MDC): IO[Unit] = super.after(er).flatMap { _ =>
    er.taskResult match {
      case TaskResult.Success(result, continue) => for {
        workflow <- Workflow(runTask.payload.workflowId)
        _ <- workflow.complete(Task.applicationName, runTask.taskId, result, continue)
        _ <- logger.debug(s"Completion of ${runTask.name}")
      } yield {
        ()
      }
      case _ => IO.unit
    }
  }
}
