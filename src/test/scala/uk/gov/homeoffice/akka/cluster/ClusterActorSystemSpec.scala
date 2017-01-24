package uk.gov.homeoffice.akka.cluster

import java.util.UUID
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import akka.actor.{Actor, ActorPath, ActorSystem, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.typesafe.config.ConfigFactory
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.cluster.PingActor.{Ping, Pong}
import uk.gov.homeoffice.akka.{ActorExpectations, ActorSystemSpecification}
import uk.gov.homeoffice.network.Network
import uk.gov.homeoffice.specs2._

class ClusterActorSystemSpec(implicit env: ExecutionEnv) extends Specification with ActorSystemSpecification with Network {
  isolated
  sequential

  trait Context extends ActorSystemContext with ActorExpectations {
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

                min-nr-of-members = 2
                auto-down-unreachable-after = 1s

                cluster {
                  name = "${UUID.randomUUID()}-test-cluster-actor-system"
                  auto-down-unreachable-after = 5s

                  seed-nodes = [{
                    host = "0.0.0.0"
                    port = $port1
                  }, {
                    host = "0.0.0.0"
                    port = $port2
                  }, {
                    host = "0.0.0.0"
                    port = $port3
                  }]
                }

                test {
                  single-expect-default = 30 seconds
                  default-timeout = 30 seconds
                }
              }""")

            clusterActorSystem = new ClusterActorSystem(config)

            val clusterActorSystems = (1 to numberOfNodes) map { node =>
              val actorSystem: ActorSystem = clusterActorSystem.node(node)

              // Assert all configured nodes for cluster (even though we may not start them all up).
              val seedNodes = actorSystem.settings.config.getList("akka.cluster.seed-nodes")

              seedNodes.toList.map(_.atKey("seed-node").getString("seed-node")) must beLike {
                case List(node1, node2, node3) =>
                  node1 mustEqual s"akka.tcp://${actorSystem.name}@0.0.0.0:$port1"
                  node2 mustEqual s"akka.tcp://${actorSystem.name}@0.0.0.0:$port2"
                  node3 mustEqual s"akka.tcp://${actorSystem.name}@0.0.0.0:$port3"
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

    def ping(system: ActorSystem, actorPath: String): Future[Boolean] = {
      val ponged = Promise[Boolean]()

      val actor = system.actorOf(
        Props {
          new Actor {
            override def receive: Receive = {
              case Ping => context.actorSelection(actorPath) ! Ping
              case Pong(_, _) => ponged success true
            }
          }
        }
      )

      actor ! Ping

      ponged.future
    }
  }

  "Cluster actor system" should {
    "not start up with only 1 node" in new Context {
      val (cluster, Seq(_)) = clusterActorSystems(1)

      eventuallyExpectMsg[MemberJoined] {
        case MemberJoined(_) => ok
      }

      expectNoMsg(10 seconds)
    }

    "start up with 2 nodes" in new Context {
      val (cluster, Seq(_, _)) = clusterActorSystems(2)

      twice {
        eventuallyExpectMsg[MemberJoined] {
          case MemberJoined(_) => ok
        }
      }

      twice {
        eventuallyExpectMsg[MemberUp] {
          case MemberUp(_) => ok
        }
      }
    }

    "non seed node joins" in new Context {
      val (cluster, Seq(actorSystem, _)) = clusterActorSystems(2)

      twice {
        expectMsgType[MemberJoined](10 seconds)
      }

      twice {
        expectMsgType[MemberUp](10 seconds)
      }

      val extraActorSystem = freeport() { port =>
        clusterActorSystem.node(host = "0.0.0.0", port = port)
      }

      eventuallyExpectMsg[MemberJoined] {
        case MemberJoined(_) => ok
      }
    }

    "not duplicate a node - if a node is generated (asked for) more than once, a 'cached' version is given" in new Context {
      val (cluster, Seq(actorSystem)) = clusterActorSystems(1)

      clusterActorSystem.node(1) mustEqual actorSystem
    }
  }

  "Cluster singleton" should {
    "not have a singleton actor running when only 1 node is running" in new Context {
      val (cluster, clusteredActorSystems @ Seq(clusteredActorSystem1)) = clusterActorSystems(1)

      clusteredActorSystems.zipWithIndex foreach { case (clusteredActorSystem, index) =>
        clusteredActorSystem.actorOf(PingActor.props(clusteredActorSystem, index + 1), "ping-actor")
      }

      eventuallyExpectMsg[MemberJoined] {
        case MemberJoined(_) => ok
      }

      // With only 1 node running, and configured to need at least 2 to form a cluster.
      expectNoMsg(10 seconds)

      info(s"Pinging.....")
      clusteredActorSystem1.actorSelection(s"akka://${clusteredActorSystem1.name}/user/ping-actor/singleton") ! Ping
      expectNoMsg(10 seconds)
    }

    "run singleton actor for 2 running nodes" in new Context {
      val (cluster, clusteredActorSystems @ Seq(clusteredActorSystem1, clusteredActorSystem2)) = clusterActorSystems(2)

      clusteredActorSystems.zipWithIndex foreach { case (clusteredActorSystem, index) =>
        clusteredActorSystem.actorOf(PingActor.props(clusteredActorSystem, index + 1), "ping-actor")
      }

      twice {
        eventuallyExpectMsg[MemberJoined] {
          case MemberJoined(_) => ok
        }
      }

      // With 2 nodes running, a singleton actor can be pinged.
      eventually(retries = 10, sleep = 10 seconds) {
        val ponged = Await.result(ping(clusteredActorSystem1, s"/user/ping-actor/singleton"), 5 seconds)
        ponged must beTrue
      }
    }

    "run singleton actor for 2 running nodes - using distributed pub/sub" in new Context {
      case object ActorRunning

      val (cluster, clusteredActorSystems @ Seq(clusteredActorSystem1, _)) = clusterActorSystems(2)

      clusteredActorSystems.zipWithIndex foreach { case (clusteredActorSystem, index) =>
        clusteredActorSystem.actorOf(PingActor.props(clusteredActorSystem, index + 1), "ping-actor")
      }

      twice {
        eventuallyExpectMsg[MemberJoined] {
          case MemberJoined(_) => ok
        }
      }

      // With 2 nodes running, a singleton actor can be pinged by publishing to a known "topic".
      eventually(retries = 10, sleep = 2 seconds) {
        clusteredActorSystem1 actorOf Props {
          new Actor {
            override def preStart(): Unit = {
              val mediator = DistributedPubSub(context.system).mediator
              mediator ! Publish("content", Ping)
            }

            override def receive: Receive = {
              case Pong(actorPath, id) => testActor ! ActorRunning
            }
          }
        }

        expectMsgType[ActorRunning.type](10 seconds)
      }
    }

    "run singleton actor for 2 running nodes, but then fail upon bringing down the leading node" in new Context {
      val (cluster, clusteredActorSystems @ Seq(clusteredActorSystem1, clusteredActorSystem2)) = clusterActorSystems(2)

      clusteredActorSystems.zipWithIndex foreach { case (clusteredActorSystem, index) =>
        clusteredActorSystem.actorOf(PingActor.props(clusteredActorSystem, index + 1), "ping-actor")
      }

      twice {
        eventuallyExpectMsg[MemberJoined] {
          case MemberJoined(_) => ok
        }
      }

      // With 2 nodes running, a singleton actor can be pinged.
      eventually(retries = 10, sleep = 10 seconds) {
        val ponged = Await.result(ping(clusteredActorSystem1, s"/user/ping-actor/singleton"), 5 seconds)
        ponged must beTrue
      }

      // 1 node leaves the cluster.
      cluster.down(cluster.selfAddress)

      eventuallyExpectMsg[MemberRemoved] {
        case MemberRemoved(_, _) => ok
      }

      info(s"Pinging.....")
      clusteredActorSystem2.actorSelection(s"akka://${clusteredActorSystem2.name}/user/ping-actor/singleton") ! Ping

      expectMsgPF(30 seconds) {
        case Pong(_, _) => ko
        case _ => ok
      }
    }

    "run singleton actor for 3 running nodes, even after bringing down the leading node" in new Context {
      val (cluster, clusteredActorSystems @ Seq(clusteredActorSystem1, clusteredActorSystem2, clusteredActorSystem3)) = clusterActorSystems(3)

      clusteredActorSystems.zipWithIndex foreach { case (clusteredActorSystem, index) =>
        clusteredActorSystem.actorOf(PingActor.props(clusteredActorSystem, index + 1), "ping-actor")
      }

      times(3) {
        eventuallyExpectMsg[MemberJoined] {
          case MemberJoined(_) => ok
        }
      }

      // With 3 nodes running, a singleton actor can be pinged.
      eventually(retries = 10, sleep = 10 seconds) {
        val ponged = Await.result(ping(clusteredActorSystem1, s"/user/ping-actor/singleton"), 5 seconds)
        ponged must beTrue
      }

      // 1 node leaves the cluster.
      cluster.down(cluster.selfAddress)

      eventuallyExpectMsg[MemberRemoved] {
        case MemberRemoved(_, _) => ok
      }

      // Singleton actor can still be pinged
      eventually(retries = 10, sleep = 10 seconds) {
        val pongedAgain = ping(clusteredActorSystem2, s"/user/ping-actor/singleton")
        pongedAgain must beEqualTo(true).await
      }
    }
  }
}

object PingActor {
  case object Ping

  case class Pong(a: ActorPath, id: Int)

  def props(system: ActorSystem, id: Int) = ClusterSingletonManager.props(
    singletonProps = Props(new PingActor(id)),
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system)
  )
}

class PingActor(id: Int) extends Actor {
  import PingActor._

  val mediator = DistributedPubSub(context.system).mediator

  // Subscribe to the topic named "content"
  mediator ! Subscribe("content", self)

  override def receive: Receive = {
    case Ping => sender() ! Pong(self.path, id)
  }
}