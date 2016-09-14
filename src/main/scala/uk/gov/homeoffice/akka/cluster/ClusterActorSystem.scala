package uk.gov.homeoffice.akka.cluster

import scala.collection.JavaConversions._
import scala.util.Try
import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logging
import uk.gov.homeoffice.configuration.ConfigFactorySupport

/**
  * Create an Actor System that can be clustered by enhancing an application Config,
  * which is expected to be a default configuration as per the Javadoc from [[https://typesafehub.github.io/config/latest/api/com/typesafe/config/ConfigFactory.html#load-com.typesafe.config.Config- ConfigFactory.load(Config)]]:
  * {{{
  *   * Loads a default configuration, equivalent to com.typesafe.config.ConfigFactory.load(com.typesafe.config.Config) in most cases.
  *   * This configuration should be used by libraries and frameworks unless an application provides a different one.
  *   * This method may return a cached singleton so will not see changes to system properties or config files.
  *   * (Use com.typesafe.config.ConfigFactory.invalidateCaches() to force it to reload.)
  *   * @return configuration for an application
  *   public static Config load()
  * }}}
  *
  * The config just has to provide seed nodes of the following format:
  * {{{
  *   akka {
  *     cluster {
  *       seed-nodes = [{
  *         host = "127.0.0.1"
  *         host = \${?CLUSTER_SEED_NODE_HOST_1} # If using environment variables
  *
  *         port = 2661
  *         port = \${?CLUSTER_SEED_NODE_PORT_1}
  *       }, {
  *         host = "127.0.0.1"
  *         host = \${?CLUSTER_SEED_NODE_HOST_2}
  *
  *         port = 2662
  *         port = \${?CLUSTER_SEED_NODE_PORT_2}
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
  *           host = \${?CLUSTER_SEED_NODE_HOST_1} # If using environment variables
  *
  *           port = 2661
  *           port = \${?CLUSTER_SEED_NODE_PORT_1}
  *         }
  *
  *         2 {
  *           host = "127.0.0.1"
  *           host = \${?CLUSTER_SEED_NODE_HOST_2}
  *
  *           port = 2662
  *           port = \${?CLUSTER_SEED_NODE_PORT_2}
  *         }
  *       }
  *     }
  *   }
  * }}}
  *
  * Note, that if you do not want the default cluster name of "cluster-actor-system" for this cluster actor system, then provide one e.g.
  * {{{
  *   akka {
  *     cluster {
  *       name = "yourClusterActorSystemName"
  * }}}
  */
object ClusterActorSystem {
  private val clusterActorSystem = new ClusterActorSystem(ConfigFactory.load)

  /**
    * Create/Get an Actor System as a seed node of a cluster.
    * @param node Int Representing the node number of the seed nodes e.g. node 2 of 3 seed nodes.
    *             Client code will probably want this to be given as an environment variable e.g.
    *             -Dcluster.node=1, captured in code as sys.props("cluster.node").toInt
    *             and indeed, this is the default.
    * @return ActorSystem that is part of a cluster
    */
  def apply(node: Int = sys.props("cluster.node").toInt): ActorSystem = clusterActorSystem.node(1)

  /**
    * Create/Get an Actor System to dynamically add to cluster seed nodes.
    * @param host String Of the host to dynamically add to the seed cluster nodes.
    *             Client code will probably want this to be given as an environment variable e.g. -Dcluster.host=rtp.homeoffice.gov.uk, captured in code as sys.props("cluster.host")
    * @param port Int Of the port for the given host to dynamically add to the seed cluster nodes.
    *             Client code will probably want this to be given as an environment variable e.g. -Dcluster.port=2665, captured in code as sys.props("cluster.port").toInt
    * @return ActorSystem that is part of a cluster
    */
  def apply(host: String, port: Int): ActorSystem = clusterActorSystem.node(host, port)
}

/**
  * Client code should use the ClusterActorSystem object.
  * However, if specific configuration is required i.e. the default use of ConfigFactory is for some reason not flexible enough,
  * client code could extend this class to provide a configuration programmatically.
  * @param config Config to be used that must include seed-nodes as illustrated in the above object's Scaladoc.
  */
protected class ClusterActorSystem(config: Config) extends ConfigFactorySupport with Logging {
  val clusterName = config.text("akka.cluster.name", "cluster-actor-system")

  val seedNodes: Seq[ClusterNode] = {
    val nodes: Seq[String] = clusterSeedNodes
    info(s"Cluster $clusterName configured with seed nodes: ${nodes.mkString(", ")}")

    nodes.zipWithIndex map { case (node, index) =>
      val nodeNumber = index + 1
      val HostPort = """.*?@(.*?):(\d*).*""".r
      val HostPort(host, port) = node
      info(s"Configuring cluster seed node $nodeNumber as $host:$port in cluster '$clusterName'")

      ClusterNode(nodeNumber, host, port.toInt, clusterConfig(host, port.toInt, nodes))
    }
  }

  /**
    * Create/Get an Actor System as a seed node of a cluster.
    * @param node Int Representing the node number of the seed nodes e.g. node 2 of 3 seed nodes.
    *             Client code will probably want this to be given as an environment variable e.g.
    *             -Dcluster.node=1, captured in code as sys.props("cluster.node").toInt, and indeed, this is the default.
    * @return ActorSystem that is part of a cluster
    */
  def node(node: Int = sys.props("cluster.node").toInt): ActorSystem = seedNodes(node - 1).actorSystem

  /**
    * Create/Get an Actor System to dynamically add to cluster seed nodes.
    * @param host String Of the host to dynamically add to the seed cluster nodes.
    *             Client code will probably want this to be given as an environment variable e.g. -Dcluster.host=rtp.homeoffice.gov.uk, captured in code as sys.props("cluster.host")
    * @param port Int Of the port for the given host to dynamically add to the seed cluster nodes.
    *             Client code will probably want this to be given as an environment variable e.g. -Dcluster.port=2665, captured in code as sys.props("cluster.port").toInt
    * @return ActorSystem that is part of a cluster
    */
  def node(host: String, port: Int): ActorSystem = {
    def createClusterConfig: Config = {
      info(s"Configuring dynamically $host:$port in cluster '$clusterName'")
      clusterConfig(host, port, clusterSeedNodes)
    }

    ActorSystem(clusterName, createClusterConfig)
  }

  def clusterSeedNodes: Seq[String] = Try {
    config.getList("akka.cluster.seed-nodes") map {
      _.atKey("seed-node")
    } map { seedNode =>
      val host = seedNode.getString("seed-node.host")
      val port = seedNode.getInt("seed-node.port")
      s""""akka.tcp://$clusterName@$host:$port""""
    }
  } getOrElse {
    config getStringList "akka.cluster.seed-nodes" map { s => s""""$s"""" }
  }

  def clusterConfig(host: String, port: Int, seedNodes: Seq[String]): Config = ConfigFactory.parseString(s"""
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

  case class ClusterNode(node: Int, host: String, port: Int, c: Config) {
    lazy val actorSystem = {
      info(s"Booting Cluster actor system node $node on $host:$port")
      ActorSystem(clusterName, c)
    }
  }
}