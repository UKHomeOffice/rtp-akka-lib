package uk.gov.homeoffice.akka.cluster

import java.util.UUID
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import akka.actor.{Actor, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import com.typesafe.config.ConfigFactory
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.ActorSystemSpecification
import uk.gov.homeoffice.network.Network
import uk.gov.homeoffice.specs2._

class ClusterActorSystemSpec extends Specification with ActorSystemSpecification with Network {
  sequential // Simply because each example can slow each down when running in parallel that includes the likelyhood of timeouts.

  trait Context extends ActorSystemContext {
    var clusterActorSystem: ClusterActorSystem = _

    override def around[R: AsResult](r: => R): Result = {
      val result = super.around(r)

      // TODO Remove hardcoding of 3 - applies to the TODO below
      val nodeTerminations = Future sequence {
        (1 to 3).map(clusterActorSystem.node(_).terminate())
      }

      Await.ready(nodeTerminations, 30 seconds)

      result
    }

    def clusterActorSystems(numberOfNodes: Int): (Cluster, Seq[ActorSystem]) = {
      require(numberOfNodes > 0)

      // TODO Configurable number of seed nodes
      freeport() { port1 =>
        freeport() { port2 =>
          freeport() { port3 =>
            val config = ConfigFactory.parseString(s"""
              akka {
                stdout-loglevel = off
                loglevel = off

                cluster {
                  name = "${UUID.randomUUID()}-test-cluster-actor-system"
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

            clusterActorSystem = new ClusterActorSystem(config)

            val clusterActorSystems = (1 to numberOfNodes) map { node =>
              val actorSystem: ActorSystem = clusterActorSystem.node(node)

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
      expectNoMsg(10 seconds)
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
        clusterActorSystem.node(host = "127.0.0.1", port = port)
      }

      expectMsgType[MemberJoined](30 seconds)
    }

    "not duplicate a node - if a node is generated (asked for) more than once, a 'cached' version is given" in new Context {
      val (cluster, Seq(actorSystem)) = clusterActorSystems(1)

      clusterActorSystem.node(1) mustEqual actorSystem
    }
  }
}