package uk.gov.homeoffice.akka.cluster

import scala.collection.JavaConversions._
import scala.util.Try
import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logging

/**
  * Create an Actor System that can be clustered by enhancing a given application Config.
  * The given config just has to provide seed nodes of the following format:
  * <pre>
  *   akka {
  *     cluster {
  *       seed-nodes = [{
  *         host = "127.0.0.1"
  *         host = ${?CLUSTER_SEED_NODE_HOST_1} # If using environment variables
  *
  *         port = 2661
  *         port = ${?CLUSTER_SEED_NODE_PORT_1}
  *       }, {
  *         host = "127.0.0.1"
  *         host = ${?CLUSTER_SEED_NODE_HOST_2}
  *
  *         port = 2662
  *         port = ${?CLUSTER_SEED_NODE_PORT_2}
  *       }]
  *     }
  *   }
  *
  *   Or maybe
  *
  *   akka {
  *     cluster {
  *       seed-nodes {
  *         1 {
  *           host = "127.0.0.1"
  *           host = ${?CLUSTER_SEED_NODE_HOST_1} # If using environment variables
  *
  *           port = 2661
  *           port = ${?CLUSTER_SEED_NODE_PORT_1}
  *         }
  *
  *         2 {
  *           host = "127.0.0.1"
  *           host = ${?CLUSTER_SEED_NODE_HOST_2}
  *
  *           port = 2662
  *           port = ${?CLUSTER_SEED_NODE_PORT_2}
  *         }
  *       }
  *     }
  *   }
  * </pre>
  */
object ClusterActorSystem extends Logging {
  /**
    * Create an Actor System as a seed node of a cluster.
    * @param name String The name of the actor system where each node of a cluster must have the same name.
    * @param config Config Where this object enhances the given Config with cluster configuration.
    *               This given config can override anything configured by this object.
    * @param node Int Representing the node number of the seed nodes e.g. node 2 of 3 seed nodes.
    *             Client code will probably want this to be given as an environment variable e.g. -Dcluster.node=1, captured in code as sys.props("cluster.node").toInt
    * @return ActorSystem
    */
  def apply(name: String, config: Config, node: Int): ActorSystem = {
    def createClusterConfig: Config = {
      val seedNodes: Seq[String] = clusterSeedNodes(name, config)
      info(s"Cluster node $node with seed nodes: ${seedNodes.mkString(", ")}")

      val HostPort = """.*?@(.*?):(\d*).*""".r
      val HostPort(host, port) = seedNodes.get(node - 1)
      info(s"Configuring node $node on $host:$port in cluster '$name'")

      clusterConfig(name, config, host, port.toInt, seedNodes)
    }

    ActorSystem(name, createClusterConfig)
  }

  /**
    * Create an Actor System to dynamically add to cluster seed nodes.
    * @param name String The name of the actor system where each node of a cluster must have the same name.
    * @param config Config Where this object enhances the given Config with cluster configuration.
    *               This given config can override anything configured by this object.
    * @param host String Of the host to dynamically add to the seed cluster nodes.
    *             Client code will probably want this to be given as an environment variable e.g. -Dcluster.host=rtp.homeoffice.gov.uk, captured in code as sys.props("cluster.host")
    * @param port Int Of the port for the given host to dynamically add to the seed cluster nodes.
    *             Client code will probably want this to be given as an environment variable e.g. -Dcluster.port=2665, captured in code as sys.props("cluster.port").toInt
    * @return ActorSystem
    */
  def apply(name: String, config: Config, host: String, port: Int): ActorSystem = {
    def createClusterConfig: Config = {
      val seedNodes: Seq[String] = clusterSeedNodes(name, config)
      info(s"Cluster dynamic node with seed nodes: ${seedNodes.mkString(", ")}")

      info(s"Configuring dynamically $host:$port in cluster '$name'")

      clusterConfig(name, config, host, port, seedNodes)
    }

    ActorSystem(name, createClusterConfig)
  }

  def clusterSeedNodes(name: String, config: Config): Seq[String] = Try {
    config.getList("akka.cluster.seed-nodes") map {
      _.atKey("seed-node")
    } map { seedNode =>
      val host = seedNode.getString("seed-node.host")
      val port = seedNode.getInt("seed-node.port")
      s""""akka.tcp://$name@$host:$port""""
    }
  } getOrElse {
    config getStringList "akka.cluster.seed-nodes" map { s => s""""$s"""" }
  }

  def clusterConfig(name: String, config: Config, host: String, port: Int, seedNodes: Seq[String]): Config = ConfigFactory.parseString(s"""
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
          ${seedNodes mkString ", "}
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
    }""").withFallback(config)
}