package uk.gov.homeoffice.akka.cluster

import scala.collection.JavaConversions._
import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory, ConfigList}
import grizzled.slf4j.Logging

object ClusterActorSystem extends Logging {
  def apply(name: String, config: Config, node: Int = sys.props("cluster.node").toInt) =
    ActorSystem(name, clusterConfig(name, config, node))

  def clusterConfig(name: String, config: Config, node: Int): Config = {
    val seedNodes: ConfigList = config.getList("akka.cluster.seed-nodes")
    info(s"Cluster seed nodes configuration: $seedNodes")

    val seedNode = seedNodes.get(node - 1).atKey("seed-node")
    val host = seedNode.getString("seed-node.host")
    val port = seedNode.getInt("seed-node.port")
    info(s"Configuring cluster node $node on $host:$port")

    ConfigFactory.parseString(s"""
      akka {
        actor {
          provider = "akka.cluster.ClusterActorRefProvider"
        }

        remote {
          enabled-transports = ["akka.remote.netty.tcp"]
          log-remote-lifecycle-events = off

          netty {
            tcp {
              hostname = "$host"
              port = $port
            }
          }
        }

        cluster {
          seed-nodes = [
            ${
              seedNodes map { _.atKey("seed-node") } map { seedNode =>
                val host = seedNode.getString("seed-node.host")
                val port = seedNode.getInt("seed-node.port")
                s""""akka.tcp://$name@$host:$port""""
              } mkString ", "
            }
          ]

          min-nr-of-members = 2
          auto-down-unreachable-after = 30s

          metrics {
            enabled = off
          }
        }

        extensions = [
          "akka.cluster.pubsub.DistributedPubSub",
          "akka.cluster.metrics.ClusterMetricsExtension"
        ]
      }
    """).withFallback(config)
  }
}