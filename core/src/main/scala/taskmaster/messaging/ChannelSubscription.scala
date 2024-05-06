package taskmaster.messaging

/**
 * Represents a token from MessageChannel when a subscription is created. Used to unsubscribe.
 *
 * @param consumerTag the unique identifier for the consumer subscription
 */
case class ChannelSubscription(consumerTag: String) extends AnyVal