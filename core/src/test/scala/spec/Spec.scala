package spec

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import profig.Profig
import scribe.{Level, Logger}

import scala.concurrent.duration._

trait Spec extends AsyncWordSpec with AsyncIOSpec with Matchers {
  Profig("scheduler.delay").merge("500 milliseconds")

  Logger("co.actioniq.scheduler").withMinimumLevel(Level.Warn).replace()
  Logger("co.actioniq.workflow").withMinimumLevel(Level.Warn).replace()
  Logger("co.actioniq.task").withMinimumLevel(Level.Warn).replace()
  Logger("spec").withMinimumLevel(Level.Warn).replace()

  /**
   * Small helper method
   */
  protected def waitFor[T](evaluate: => T,
                           expected: T,
                           timeout: FiniteDuration = 5.seconds,
                           start: Long = System.currentTimeMillis()): IO[Unit] = if (evaluate == expected) {
    IO.unit
  } else {
    val elapsed = System.currentTimeMillis() - start
    if (elapsed > timeout.toMillis) throw new RuntimeException(s"Timeout! Expected: $expected, but got: $evaluate!")
    IO.sleep(250.millis).flatMap(_ => waitFor(evaluate, expected, timeout, start))
  }
}
