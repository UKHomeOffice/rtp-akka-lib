package uk.gov.homeoffice.akka.cluster

import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logging

/**
  * For a module to be clustered, seed-nodes have to be configured, and one of the entries in seed-nodes will be the module that wishes to be part of the cluster.
  * The number of nodes in the cluster is stipulated in seed-nodes of application.conf.
  * When a module is run on a node, it is represented as one entry in seed-nodes, and we have to state which node in seed-nodes the module is.
  * E.g. if the module is to be run on node 1, represented by the first entry in seed-nodes, then we must state that this is indeed node 1 by providing the JVM property cluster.node=1
  *
  * As an example, to run locally 3 instances of a module in 3 consoles (and there must be 3 corresponding entries in seed-nodes):
  * <pre>
  *   sbt '; set javaOptions += "-Dcluster.node=1"; run'
  *   sbt '; set javaOptions += "-Dcluster.node=2"; run'
  *   sbt '; set javaOptions += "-Dcluster.node=3"; run'
  * </pre>
  *
  * Or running the assembled JAR:
  * <pre>
  *   java -Dcluster.node=1 -jar <jar name>.jar
  *   java -Dcluster.node=2 -jar <jar name>.jar
  *   java -Dcluster.node=3 -jar <jar name>.jar
  * </pre>
  *
  * An ActorSystem can be instantiated e.g.
  * ActorSystem("service-name-actor-system", hostClusterConfig.withFallback(<main configuration>))
  *
  * where application.conf would be e.g.
  * <pre>
  *   akka {
  *     actor {
  *       provider = "akka.cluster.ClusterActorRefProvider"
  *     }
  *
  *     remote {
  *       enabled-transports = ["akka.remote.netty.tcp"]
  *       log-remote-lifecycle-events = off
  *
  *       netty {
  *         tcp {
  *           hostname = "127.0.0.1"
  *           port = 0
  *         }
  *       }
  *     }
  *
  *     cluster {
  *       seed-nodes = [
  *         "akka.tcp://service-name-actor-system@127.0.0.1:2551",
  *         "akka.tcp://service-name-actor-system@127.0.0.1:2552",
  *         "akka.tcp://service-name-actor-system@127.0.0.1:2553"
  *       ]
  *
  *       min-nr-of-members = 2
  *       auto-down-unreachable-after = 30s
  *
  *       metrics {
  *         enabled = off
  *       }
  *     }
  *
  *     extensions = [
  *       "akka.cluster.pubsub.DistributedPubSub",
  *       "akka.cluster.metrics.ClusterMetricsExtension"
  *     ]
  *   }
  * </pre>
  */
trait Clustering extends Logging {
  def config: Config

  def hostClusterConfig: Config = {
    val NodeConfig = """.*?@(.*?):(\d*)""".r

    val node = sys.props("cluster.node").toInt
    val nodeConfig = config.getList("akka.cluster.seed-nodes").get(node - 1).unwrapped.toString

    val (host, port) = nodeConfig match {
      case NodeConfig(h, p) => (h, p.toInt)
    }

    info(s"Configured cluster node $node on $host:$port")

    ConfigFactory.parseString(s"""
      akka.remote.netty.tcp.hostname = "$host"
      akka.remote.netty.tcp.port = $port
    """)
  }
}