package uk.gov.homeoffice.akka.cluster

import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification

class ClusterActorSystemSpec extends Specification {
  "Cluster actor system" should {
    "not start up with only 1 node" in {
      todo
    }

    "start up with 2 nodes" in {
      val config = ConfigFactory.load("application.test.conf")

      val system1 = ClusterActorSystem("system-1", config, 1)
      val system2 = ClusterActorSystem("system-2", config, 2)

      ok
    }

    "start up with 3 nodes" in {
      todo
    }
  }
}