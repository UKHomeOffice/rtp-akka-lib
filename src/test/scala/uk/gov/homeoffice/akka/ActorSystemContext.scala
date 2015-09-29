package uk.gov.homeoffice.akka

import java.util.UUID
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKitBase}
import org.specs2.execute.{AsResult, Result}
import org.specs2.specification.Scope
import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logging
import uk.gov.homeoffice.specs2.ComposableAround

abstract class ActorSystemContext(config: Config = ConfigFactory.load) extends TestKitBase with ImplicitSender with Scope with ComposableAround with Logging {
  implicit lazy val system: ActorSystem = ActorSystem(UUID.randomUUID().toString, config)

  override def around[R: AsResult](r: => R): Result = {
    try {
      info(s"+ Started actor system $system")
      super.around(r)
    } finally {
      info(s"x Shutting down actor system $system")
      system.shutdown()
      system.awaitTermination(10 seconds)
    }
  }
}