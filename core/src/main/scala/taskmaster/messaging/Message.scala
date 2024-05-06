package taskmaster.messaging

/**
 * Message wraps a value and deliveryTag coming from the message queue
 */
case class Message[T](value: T, deliveryTag: Long)