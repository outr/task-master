package taskmaster.messaging

import fabric.rw._

/**
 * Configuration representation for the MessageQueue
 *
 * @param username defaults to "guest"
 * @param password defaults to "guest"
 * @param virtualHost defaults to "/"
 * @param host defaults to "localhost"
 * @param port defaults to 5672
 * @param exchangeName defaults to "taskmaster"
 * @param routingKey defaults to "taskmaster-tasks"
 * @param durable defaults to true
 * @param exclusive defaults to false
 * @param autoDelete defaults to false
 * @param mandatory defaults to true
 * @param autoAck defaults to false
 */
case class MessageQueueConfig(username: String = "guest",
                              password: String = "guest",
                              virtualHost: String = "/",
                              host: String = "localhost",
                              port: Int = 5672,
                              exchangeName: String = "taskmaster",
                              routingKey: String = "taskmaster-tasks",
                              durable: Boolean = true,
                              exclusive: Boolean = false,
                              autoDelete: Boolean = false,
                              mandatory: Boolean = true,
                              autoAck: Boolean = false)

object MessageQueueConfig {
  implicit val rw: RW[MessageQueueConfig] = RW.gen
}