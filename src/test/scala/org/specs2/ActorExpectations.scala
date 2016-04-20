package org.specs2

import scala.reflect._
import org.specs2.execute.Result
import org.specs2.matcher.{Expectable, MatchFailure, MatchResult, Matcher}
import uk.gov.homeoffice.akka.ActorSystemContext

/**
  * Actor expectations that are missing from the standard ones such as expectMsg[T]
  */
trait ActorExpectations {
  this: ActorSystemContext =>

  /**
    * An Actor may receive multiple messages, but you are only interested in one particular message.
    * This method allows you to eventually assert against the message you are looking for.
    * @param pf PartialFunction Given an Actor message that results in true/false of whether the message is the one you are interested in.
    * @param c ClassTag That keeps runtime information for this generic method
    * @tparam T Type of the message that is expected
    * @return MatchResult of "ok" if a valid expectation, otherwise "ko".
    */
  def eventuallyExpectMsg[T](pf: PartialFunction[Any, Boolean])(implicit c: ClassTag[T]): MatchResult[Any] = {
    val result = fishForMessage() {
      pf orElse { case _ => false }
    }

    if (classTag[T].runtimeClass.isInstance(result)) Matcher.result(test = true, "ok", "ko", createExpectable(None, None))
    else checkFailure(Matcher.result(test = false, "ok", "ko", createExpectable(None, None)))
  }

  /** Copied from Specs2 */
  protected def createExpectable[T](t: => T, alias: Option[String => String]): Expectable[T] = new Expectable[T](() => t) {
    override val desc: Option[String => String] = alias
    override def check[S >: T](r: MatchResult[S]): MatchResult[S] = checkFailure(r)
    override def checkResult(r: Result): Result = checkResultFailure(r)
  }

  /** Copied from Specs2 */
  protected def checkResultFailure(r: => Result): Result = r

  /** Copied from Specs2 */
  protected def checkFailure[T](m: MatchResult[T]): MatchResult[T] = checkMatchResultFailure(mapMatchResult(setStacktrace(m)))

  /** Copied from Specs2 */
  protected def checkMatchResultFailure[T](m: MatchResult[T]): MatchResult[T] = m

  /** Copied from Specs2 */
  protected def mapMatchResult[T](m: MatchResult[T]): MatchResult[T] = m

  /** Copied from Specs2 */
  protected def setStacktrace[T](m: MatchResult[T]): MatchResult[T] = m match {
    case f: MatchFailure[_] if f.trace.isEmpty => f.copy(trace = (new Exception).getStackTrace.toList)
    case other => other
  }
}