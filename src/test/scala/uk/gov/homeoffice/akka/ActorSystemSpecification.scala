package uk.gov.homeoffice.akka

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKitBase}
import com.typesafe.config.{Config, ConfigFactory}
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AfterAll
import grizzled.slf4j.Logging
import uk.gov.homeoffice.specs2.SpecificationExpectations

trait ActorSystemSpecification extends TestKitBase with ImplicitSender with AfterAll with SpecificationExpectations with Logging {
  this: SpecificationLike =>

  isolated
  sequential

  implicit lazy val config: Config = ConfigFactory.load

  implicit lazy val system: ActorSystem = ActorSystem(UUID.randomUUID().toString, config)

  def afterAll() = {
    info(s"Shutting down actor system $system")
    Await.ready(system.terminate(), 2 seconds)
  }
}