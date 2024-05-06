package spec

import cats.effect.IO
import fabric.rw._
import profig.Profig
import scribe.cats.{io => logger}
import taskmaster.messaging.{MessageQueue, ProcessResult}

import scala.concurrent.duration._

class MessageQueueSpec extends Spec {
  "MessageQueue" should {
    val johnDoe = Person("John Doe", 21)
    val janeDoe = Person("Jane Doe", 20)
    val someBody = Person("Some Body", 123)

    val sanJose = Location("San Jose", "California")
    val nyc = Location("New York City", "New York")
    val okc = Location("Oklahoma City", "Oklahoma")

    lazy val people = MessageQueue.channel[Person]("people")
    lazy val locations = MessageQueue.channel[Location]("locations")

    "initialize" in {
      Profig.initConfiguration()
      succeed
    }
    "purge the people queue" in {
      people.purge().map { _ =>
        succeed
      }
    }
    "purge the locations queue" in {
      locations.purge().map { _ =>
        succeed
      }
    }
    "publish two person messages successfully" in {
      people.publish(johnDoe, janeDoe).map { _ =>
        succeed
      }
    }
    "publish two location messages successfully" in {
      locations.publish(sanJose, nyc).map { _ =>
        succeed
      }
    }
    "poll for two people" in {
      for {
        result1 <- people.pollAndAcknowledge
        result2 <- people.pollAndAcknowledge
        result3 <- people.pollAndAcknowledge
      } yield {
        result1 should be(Some(johnDoe))
        result2 should be(Some(janeDoe))
        result3 should be(None)
      }
    }
    "verify the person queue is empty" in {
      people.messageCount.map { count =>
        count should be(0L)
      }
    }
    "use subscription for locations" in {
      var received = List.empty[Location]
      for {
        subscription <- IO {
          locations.subscribe { location =>
            for {
              _ <- logger.info(s"Subscription received $location")
              _ <- IO {
                received = location :: received
              }
              _ <- IO.sleep(2.seconds)
            } yield {
              ProcessResult.Acknowledge
            }
          }
        }
        _ <- waitFor(received.size, 1)
        _ <- IO.sleep(500.millis)
        l1 <- locations.pollAndAcknowledge.start
        l2 <- locations.pollAndAcknowledge.start
        result1 <- l1.joinWithNever
        result2 <- l2.joinWithNever
        _ <- locations.publish(okc)
        _ <- waitFor(received.size, 2)
        _ = locations.unsubscribe(subscription)
      } yield {
        received should be(List(okc, sanJose))
        result1 should be(Some(nyc))
        result2 should be(None)
      }
    }
    "verify the location queue is empty" in {
      locations.messageCount.map { count =>
        count should be(0L)
      }
    }
    "send a person message" in {
      people.publish(someBody).map { _ =>
        succeed
      }
    }
    "poll the person but close the channel without acknowledging" in {
      val channel = MessageQueue.channel[Person]("people")
      for {
        person <- channel.poll.map(_.map(_.value))
        _ = person should be(Some(someBody))
        _ <- channel.dispose()
      } yield {
        succeed
      }
    }
    "verify the person is immediately available to be queued again" in {
      people.pollAndAcknowledge.map { o =>
        o should be(Some(someBody))
      }
    }
    "verify the people queue is now empty" in {
      people.messageCount.map { count =>
        count should be(0)
      }
    }
    // TODO: Validate two queues aren't running concurrently for one worker
    "dispose" in {
      people.dispose().flatMap { _ =>
        MessageQueue.dispose().map { _ =>
          succeed
        }
      }
    }
  }

  case class Person(name: String, age: Int)

  object Person {
    implicit val rw: RW[Person] = RW.gen
  }

  case class Location(city: String, state: String)

  object Location {
    implicit val rw: RW[Location] = RW.gen
  }
}
