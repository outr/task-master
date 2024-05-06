package taskmaster.task

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fabric.Json
import fabric.rw._
import taskmaster.messaging.{ChannelSubscription, MessageChannel, MessageQueue, ProcessResult}

import java.util.concurrent.ConcurrentHashMap

/**
 * TaskRegistry is used to register TaskRunners with a channel
 */
class TaskRegistry private(val applicationName: String) {
  private var map = Map.empty[String, TaskRunner[_]]

  lazy val channel: MessageChannel[RunTask[Json]] = MessageQueue.channel[RunTask[Json]](applicationName)

  private var subscription: Option[ChannelSubscription] = None

  private def getOrCreateSubscription()(implicit runtime: IORuntime): ChannelSubscription = synchronized {
    if (subscription.isEmpty) {
      subscription = Some(channel.subscribe(run))
    }
    subscription.get
  }

  private def run(runTask: RunTask[Json]): IO[ProcessResult] = map(runTask.name).runWithJson(runTask)

  /**
   * The number of registered TaskRunners
   */
  def registered: Int = map.size

  /**
   * Registers a TaskRunner with this registry
   *
   * @param name the task name
   * @param create function to create the Task
   */
  def register[Payload](name: String)
                       (create: RunTask[Payload] => IO[Task[Payload]])
                       (implicit runtime: IORuntime, rw: RW[Payload]): Unit = synchronized {
    require(!map.contains(name), s"Task Registry already contains entry for $name!")
    val r = TaskRunner[Payload](name, create)(rw)
    map += name -> r
    getOrCreateSubscription()
  }

  /**
   * Convenience method to request the run of a task.
   */
  def requestRun[Payload: RW](name: String,
                                        payload: Payload,
                                        taskId: TaskId = TaskId()): IO[Unit] = {
    channel.publish(RunTask(name, taskId, payload.json))
  }

  /**
   * Disposes the underlying channel used by this registry
   */
  def dispose(): IO[Unit] = channel.dispose()
}

object TaskRegistry {
  private val map = new ConcurrentHashMap[String, TaskRegistry]

  def apply(applicationName: String,
            registered: Boolean = true): TaskRegistry = if (registered) {
    map.computeIfAbsent(applicationName, (applicationName: String) => {
      new TaskRegistry(applicationName)
    })
  } else {
    new TaskRegistry(applicationName)
  }

  def worker(applicationName: String, handlers: TaskHandler[_]*)
            (implicit runtime: IORuntime): TaskRegistry = {
    val r = TaskRegistry(applicationName)
    handlers.toList.foreach(_.register(r))
    r
  }
}