package taskmaster.task

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fabric.rw._

/**
 *
 */
trait TaskHandler[Payload] {
  implicit val rw: RW[Payload]

  protected lazy val defaultTaskName: String = getClass.getSimpleName.replace("$", "")

  def taskName: String = defaultTaskName

  def create(runTask: RunTask[Payload]): IO[Task[Payload]]

  def register(registry: TaskRegistry)
              (implicit runtime: IORuntime): Unit = registry.register(
    name = taskName
  )(create)(runtime, rw)

  def requestRun(caller: TaskRegistry,
                 payload: Payload,
                 dependsOn: Set[TaskId] = Set.empty,
                 taskId: TaskId = TaskId()): IO[Unit] = caller.requestRun(taskName, payload, taskId)

  def apply(payload: Payload,
            dependencies: Set[TaskInstanceCreator[_]] = Set.empty,
            taskId: TaskId = TaskId()): TaskInstanceCreator[Payload] = TaskInstanceCreator(
    payload = payload,
    handler = this,
    dependencies = dependencies,
    taskId = taskId
  )
}

object TaskHandler {
  def register(registry: TaskRegistry, handlers: TaskHandler[_]*)
              (implicit runtime: IORuntime): Unit = handlers.toList.foreach(_.register(registry))
}