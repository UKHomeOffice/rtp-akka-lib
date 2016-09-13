package uk.gov.homeoffice.akka.cluster

import java.util.UUID
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import akka.actor.{Actor, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import com.typesafe.config.ConfigFactory
import org.jboss.netty.channel.ChannelException
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.ActorSystemSpecification
import uk.gov.homeoffice.io.Network
import uk.gov.homeoffice.specs2._

class ClusterActorSystemSpec extends Specification with ActorSystemSpecification with Network {
  sequential // Simply because each example can slow each down when running in parallel that includes the likelyhood of timeouts.

  trait Context extends ActorSystemContext {
    private var actorSystems = Seq.empty[ActorSystem]

    override def around[R: AsResult](r: => R): Result = {
      val result = super.around(r)
      actorSystems foreach { _.terminate() }
      result
    }

    def clusterActorSystems(numberOfNodes: Int): (Cluster, Seq[ActorSystem]) = {
      require(numberOfNodes > 0)

      freeport() { port1 =>
        freeport() { port2 =>
          freeport() { port3 =>
            // TODO Configurable number of nodes
            val config = ConfigFactory.parseString(s"""
              akka {
                cluster {
                  auto-down-unreachable-after = 5s

                  seed-nodes = [{
                    host = "127.0.0.1"
                    port = $port1
                  }, {
                    host = "127.0.0.1"
                    port = $port2
                  }, {
                    host = "127.0.0.1"
                    port = $port3
                  }]
                }
              }""")

            val actorSystemName = s"${UUID.randomUUID()}-test-actor-system"

            val clusterActorSystems = (1 to numberOfNodes) map { node =>
              val actorSystem = ClusterActorSystem(actorSystemName, config, node)
              actorSystems = actorSystems :+ actorSystem

              // Assert all configured nodes for cluster (even though we may not start them all up).
              val seedNodes = actorSystem.settings.config.getList("akka.cluster.seed-nodes")

              seedNodes.toList.map(_.atKey("seed-node").getString("seed-node")) must beLike {
                case List(node1, node2, node3) =>
                  node1 mustEqual s"akka.tcp://${actorSystem.name}@127.0.0.1:$port1"
                  node2 mustEqual s"akka.tcp://${actorSystem.name}@127.0.0.1:$port2"
                  node3 mustEqual s"akka.tcp://${actorSystem.name}@127.0.0.1:$port3"
              }

              actorSystem
            }

            val cluster = Cluster(clusterActorSystems(0))

            val listeningActor = clusterActorSystems(0) actorOf Props {
              new Actor {
                override def receive: Receive = {
                  case m: MemberEvent => testActor ! m
                }
              }
            }

            cluster.subscribe(subscriber = listeningActor, to = classOf[MemberEvent])

            (cluster, clusterActorSystems)
          }
        }
      }
    }
  }

  "Cluster actor system" should {
    "not start up with only 1 node" in new Context {
      val (cluster, Seq(_)) = clusterActorSystems(1)

      expectMsgType[MemberJoined](30 seconds)
      expectNoMsg(30 seconds)
    }

    "start up with 2 nodes" in new Context {
      val (cluster, Seq(_, _)) = clusterActorSystems(2)

      twice {
        expectMsgType[MemberJoined](30 seconds)
      }

      twice {
        expectMsgType[MemberUp](30 seconds)
      }
    }

    "non seed node joins" in new Context {
      val (cluster, Seq(actorSystem, _)) = clusterActorSystems(2)

      twice {
        expectMsgType[MemberJoined](30 seconds)
      }

      twice {
        expectMsgType[MemberUp](30 seconds)
      }

      val extraActorSystem = freeport() { port =>
        ClusterActorSystem(actorSystem.name, actorSystem.settings.config, host = "127.0.0.1", port = port)
      }

      expectMsgType[MemberJoined](30 seconds)
    }

    "start up with 3 nodes" in new Context {
      val (cluster, Seq(_, _, _)) = clusterActorSystems(3)

      times(3) {
        expectMsgType[MemberJoined](30 seconds)
      }

      times(3) {
        expectMsgType[MemberUp](30 seconds)
      }
    }

    "not allow 2 nodes on the same box to use the same port" in new Context {
      val (cluster, Seq(actorSystem)) = clusterActorSystems(1)

      ClusterActorSystem(actorSystem.name, actorSystem.settings.config, 1) must throwA[ChannelException]
    }
  }
}