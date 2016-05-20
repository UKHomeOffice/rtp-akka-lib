package uk.gov.homeoffice.akka

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit._
import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigFactory._
import de.flapdoodle.embed.process.runtime.Network._

trait ClusterSingleton {
  def cluster(numberOfNodes: Int): Seq[ActorSystem] = try {
    val ports = 1 to numberOfNodes map { _ => freePort }

    val seedNodes = ports map { port =>
      s""""akka.tcp://my-actor-system@127.0.0.1:$port""""
    } mkString ", "

    val config: Config = load(parseString(s"""
      akka {
        actor {
          provider = "akka.cluster.ClusterActorRefProvider"
        }

        remote {
          enabled-transports = ["akka.remote.netty.tcp"]

          netty.tcp {
            hostname = "127.0.0.1"
            port = 0 # To be overridden in code for each running node in a cluster
          }
        }

        cluster {
          seed-nodes = [ $seedNodes ]
          # roles = ["my-service"]
          min-nr-of-members = 2
          auto-down-unreachable-after = 30s
        }

        extensions = ["akka.cluster.pubsub.DistributedPubSub"]
      }"""))

    ports map { port =>
      ActorSystem("my-actor-system", ConfigFactory.parseString(s"akka.remote.netty.tcp.port = $port").withFallback(config))
    }
  } catch {
    case t: Throwable =>
      println(s"Error in starting up an actor system because of: ${t.getMessage}... Will try again")
      TimeUnit.SECONDS.sleep(1)
      cluster(numberOfNodes)
  }

  def freePort: Int = {
    val port = getFreeServerPort

    // Avoid standard Mongo ports in case a standalone Mongo is running.
    if ((27017 to 27027) contains port) {
      MILLISECONDS.sleep(10)
      freePort
    } else {
      port
    }
  }

  def clusterConfig(ports: Seq[Int]): Config = load(parseString(s"""
    akka {
      actor {
        provider = "akka.cluster.ClusterActorRefProvider"
      }

      remote {
        enabled-transports = ["akka.remote.netty.tcp"]

        netty.tcp {
          hostname = "127.0.0.1"
          port = 0 # To be overridden in code for each running node in a cluster
        }
      }

      cluster {
        seed-nodes = [ "${ports map { port => s"akka.tcp://my-actor-system@127.0.0.1:$port" } mkString ", "}" ]
        roles = ["my-service"]
        min-nr-of-members = 2
        auto-down-unreachable-after = 30s
      }

      extensions = ["akka.cluster.pubsub.DistributedPubSub"]
    }"""))
}