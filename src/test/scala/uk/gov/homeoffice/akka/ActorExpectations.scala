package uk.gov.homeoffice.akka

import scala.concurrent.duration._
import scala.reflect._
import akka.testkit.TestKitBase
import org.specs2.matcher.Matcher._
import org.specs2.matcher.{MatchResult, _}

trait ActorExpectations {
  this: TestKitBase =>

  /**
    * An Actor may receive multiple messages, but you are only interested in one particular message.
    * This method allows you to eventually assert against the message you are looking for.
    * @param pf PartialFunction Given an Actor message that results in true/false of whether the message is the one you are interested in.
    * @param c  ClassTag That keeps runtime information for this generic method
    * @tparam T Type of the message that is expected
    * @return MatchResult of "ok" if a valid expectation, otherwise "ko".
    */
  def eventuallyExpectMsg[T](pf: PartialFunction[Any, Boolean], timeout: Duration = 30 seconds)(implicit c: ClassTag[T]): MatchResult[Any] = {
    val message = fishForMessage(max = timeout) {
      pf orElse { case _ => false }
    }

    if (classTag[T].runtimeClass.isInstance(message)) {
      result(test = true, "ok", "ko", createExpectable(None, None))
    } else {
      result(test = false, "ok", "ko", createExpectable(None, None))
    }
  }
}