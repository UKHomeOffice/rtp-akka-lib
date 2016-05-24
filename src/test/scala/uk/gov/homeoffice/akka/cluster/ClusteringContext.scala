package uk.gov.homeoffice.akka.cluster

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit._
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory._
import com.typesafe.config.{Config, ConfigFactory}
import org.specs2.matcher.Scope
import de.flapdoodle.embed.process.runtime.Network._

trait ClusteringContext {
  this: Scope =>

  def cluster(numberOfNodes: Int, extraConfig: Config = ConfigFactory.empty()): Seq[ActorSystem] = try {
    val ports: Seq[Int] = 1 to numberOfNodes map { _ => freePort }

    val config: Config = clusterConfig(ports)

    ports map { port =>
      ActorSystem("my-actor-system", ConfigFactory.parseString(s"akka.remote.netty.tcp.port = $port").withFallback(extraConfig).withFallback(config))
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

  /**
    * # Disable legacy metrics in akka-cluster.
    * akka.cluster.metrics.enabled=off
    *
    * # Enable metrics extension in akka-cluster-metrics.
    * akka.extensions=["akka.cluster.metrics.ClusterMetricsExtension"]
    *
    * # Sigar native library extract location during tests.
    * # Note: use per-jvm-instance folder when running multiple jvm on one host.
    * akka.cluster.metrics.native-library-extract-folder=${user.dir}/target/native
    */
  def clusterConfig(ports: Seq[Int]): Config = {
    val seedNodes = ports map { port =>
      s""""akka.tcp://my-actor-system@127.0.0.1:$port""""
    } mkString ", "

    load(parseString(s"""
      akka {
        actor {
          provider = "akka.cluster.ClusterActorRefProvider"
        }

        remote {
          enabled-transports = ["akka.remote.netty.tcp"]
          log-remote-lifecycle-events = off

          netty {
            tcp {
              hostname = "127.0.0.1"
              port = 0 # To be overridden in code for each running node in a cluster
            }
          }
        }

        cluster {
          seed-nodes = [ $seedNodes ]
          # roles = ["my-service"]
          min-nr-of-members = 2
          # auto-down-unreachable-after = 30s

          metrics {
            enabled = off
          }
        }

        extensions = [
          "akka.cluster.pubsub.DistributedPubSub",
          "akka.cluster.metrics.ClusterMetricsExtension"
        ]
      }"""))
  }
}