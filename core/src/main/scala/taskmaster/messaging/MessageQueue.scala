package taskmaster.messaging

import cats.effect.IO
import com.rabbitmq.client.{Connection, ConnectionFactory}
import fabric.rw._
import profig.Profig

import scala.util.Try

/**
 * MessageQueue provides an abstraction for publishing and subscribing to messages on the message queue
 */
object MessageQueue {
  lazy val config: MessageQueueConfig = Profig("mq").as[MessageQueueConfig]

  private[messaging] lazy val connection: Connection = {
    val factory = new ConnectionFactory
    factory.setUsername(config.username)
    factory.setPassword(config.password)
    factory.setVirtualHost(config.virtualHost)
    factory.setHost(config.host)
    factory.setPort(config.port)
    factory.newConnection()
  }

  /**
   * Creates a new MessageChannel for the given queue name expecting messages of T
   */
  def channel[T: RW](queueName: String): MessageChannel[T] = new MessageChannel[T](queueName)

  /**
   * Closes the connection and releases resources associated with this MessageQueue. Should not be used in tests as it
   * will break other tests running in the same JVM instance.
   */
  def dispose(): IO[Unit] = IO {
    Try(connection.close())
  }
}