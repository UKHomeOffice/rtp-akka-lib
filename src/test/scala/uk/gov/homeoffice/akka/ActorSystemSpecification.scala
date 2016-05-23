package uk.gov.homeoffice.akka

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKitBase}
import com.typesafe.config.{Config, ConfigFactory}
import org.specs2.execute.{AsResult, Result}
import org.specs2.matcher.MatchResult
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.Scope
import grizzled.slf4j.Logging
import uk.gov.homeoffice.specs2.ComposableAround

trait ActorSystemSpecification extends Logging {
  this: SpecificationLike =>

  isolated
  sequential

  implicit def any2Success[R](r: R): Result = success

  implicit def any2MatchResult[R](r: R): MatchResult[Any] = ok

  abstract class ActorSystemContext(val config: Config = ConfigFactory.load) extends TestKitBase with ImplicitSender with Scope with ComposableAround {
    implicit lazy val system: ActorSystem = ActorSystem(UUID.randomUUID().toString, config)

    override def around[R: AsResult](r: => R): Result = try {
      info(s"Started actor system $system")
      super.around(r)
    } finally {
      info(s"Shutting down actor system $system")
      Await.ready(system.terminate(), 2 seconds)
    }
  }
}