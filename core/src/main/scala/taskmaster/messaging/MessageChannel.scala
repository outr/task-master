package taskmaster.messaging

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.rabbitmq.client._
import fabric.io.{Format, JsonFormatter, JsonParser}
import fabric.rw._

import scala.util.Try

/**
 * MessageChannel is created for a queue name from MessageQueue
 *
 * NOTE: RabbitMQ's Channel is not thread-safe, hence the synchronized blocks everywhere
 */
class MessageChannel[T](queueName: String)(implicit val rw: RW[T]) { messageChannel =>
  private def config = MessageQueue.config

  private val exchangeName = config.exchangeName
  private val routingKey = s"${config.routingKey}-$queueName"
  private lazy val channel: Channel = {
    val c = MessageQueue.connection.createChannel()
    val arguments: java.util.Map[String, AnyRef] = null
    c.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT, config.durable)
    c.queueDeclare(queueName, config.durable, config.exclusive, config.autoDelete, arguments).getQueue
    c.queueBind(queueName, exchangeName, routingKey)
    c.basicQos(1)
    c
  }

  /**
   * Publish messages to this channel
   */
  def publish(values: T*): IO[Unit] = if (values.isEmpty) {
    IO.unit
  } else {
    IO {
      val json = values.head.json
      val jsonString = JsonFormatter.Default(json)
      val bytes = jsonString.getBytes("UTF-8")
      messageChannel.synchronized {
        channel.basicPublish(
          exchangeName,
          routingKey,
          config.mandatory,
          MessageProperties.PERSISTENT_TEXT_PLAIN,
          bytes
        )
      }
    }.flatMap { _ =>
      publish(values.tail: _*)
    }
  }

  /**
   * Creates a subscription to process incoming messages on this channel. The function provided for `f` represents the
   * processor for events coming in. The returned `ChannelSubscription` is utilized to unsubscribe.
   *
   * This defaults to receive one message at a time to allow worker scaling up.
   */
  def subscribe(f: T => IO[ProcessResult])
               (implicit runtime: IORuntime): ChannelSubscription = messageChannel.synchronized {
    val consumerTag = channel.basicConsume(queueName, config.autoAck, new DefaultConsumer(channel) {
      override def handleDelivery(consumerTag: String,
                                  envelope: Envelope,
                                  properties: AMQP.BasicProperties,
                                  body: Array[Byte]): Unit = {
        val jsonString = new String(body, "UTF-8")
        try {
          val deliveryTag = envelope.getDeliveryTag
          val json = JsonParser(jsonString, Format.Json)
          val value = json.as[T]
          val message = Message(value, deliveryTag)
          val io = f(message.value).flatMap {
            case ProcessResult.Acknowledge => acknowledge(message)
            case ProcessResult.Reject => reject(message)
          }.redeemWith(t => IO {
            scribe.error(s"Error while processing MQ event for: $message", t)
          }, IO.pure)
          io.unsafeRunAndForget()
        } catch {
          case t: Throwable =>
            scribe.error(s"Error while parsing MQ event (consumerTag: $consumerTag): $jsonString", t)
        }
      }
    })
    ChannelSubscription(consumerTag)
  }

  /**
   * Unsubscribe from an existing subscription
   */
  def unsubscribe(subscription: ChannelSubscription): Unit = messageChannel.synchronized {
    channel.basicCancel(subscription.consumerTag)
  }

  /**
   * Poll for a message. This is not recommended as the performance is not ideal. Usage of subscribe is much better.
   */
  def poll: IO[Option[Message[T]]] = IO {
    val option = messageChannel.synchronized(Option(channel.basicGet(queueName, config.autoAck)))
    option match {
      case Some(response) =>
        val deliveryTag = response.getEnvelope.getDeliveryTag
        val body = response.getBody
        val jsonString = new String(body, "UTF-8")
        val json = JsonParser(jsonString, Format.Json)
        val value = json.as[T]
        Some(Message(value, deliveryTag))
      case None => None
    }
  }

  /**
   * Works like poll except automatically acknowledges the message
   */
  def pollAndAcknowledge: IO[Option[T]] = poll.flatMap {
    case Some(message) => acknowledge(message).map { _ =>
      Some(message.value)
    }
    case None => IO.pure(None)
  }

  /**
   * Acknowledge a message to allow it to be deleted from the queue
   */
  def acknowledge(message: Message[T]): IO[Unit] = {
    IO(messageChannel.synchronized(channel.basicAck(message.deliveryTag, false)))
  }

  /**
   * Reject a message to allow it to be handled by another worker
   */
  def reject(message: Message[T]): IO[Unit] = {
    IO(messageChannel.synchronized(channel.basicNack(message.deliveryTag, false, true)))
  }

  /**
   * Retrieves this queues message count that is unacknowledged
   */
  def messageCount: IO[Long] = IO(messageChannel.synchronized(channel.messageCount(queueName)))

  /**
   * Purge all messages from this queue
   */
  def purge(): IO[Int] = IO(messageChannel.synchronized(channel.queuePurge(queueName).getMessageCount))

  /**
   * Delete the queue
   */
  def delete(): IO[Int] = IO(messageChannel.synchronized(channel.queueDelete(queueName).getMessageCount))

  /**
   * Disposes the underlying channel and releases resources associated with it
   */
  def dispose(): IO[Unit] = IO {
    Try(messageChannel.synchronized(channel.close()))
  }
}