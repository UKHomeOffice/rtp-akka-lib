package uk.gov.homeoffice.akka

import java.util.UUID
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKitBase}
import com.typesafe.config.{Config, ConfigFactory}
import org.specs2.execute.Result
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AfterAll

trait ActorSystemSpecification extends TestKitBase with ImplicitSender with AfterAll {
  this: SpecificationLike =>

  isolated
  sequential

  implicit lazy val config: Config = ConfigFactory.load

  implicit lazy final val system: ActorSystem = ActorSystem(UUID.randomUUID().toString, config)

  implicit def any2Success[R](r: R): Result = success

  def afterAll() = system.terminate()
}