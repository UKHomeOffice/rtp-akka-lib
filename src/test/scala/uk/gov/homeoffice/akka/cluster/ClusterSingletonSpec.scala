package uk.gov.homeoffice.akka.cluster

import java.util.concurrent.TimeUnit.{MILLISECONDS => _}
import scala.concurrent.duration._
import akka.actor.{Actor, ActorPath, ActorSystem, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberEvent, MemberRemoved}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.typesafe.config.ConfigFactory
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.{ActorExpectations, ActorSystemSpecification}

// TODO Migrate to using uk.gov.homeoffice.akka.cluster.ClusterActorSystem
class ClusterSingletonSpec(implicit ev: ExecutionEnv) extends Specification with ActorSystemSpecification {
  skipAll

  import PingActor._

  trait Context extends ActorSystemContext with ActorExpectations with ClusteringContext

  "Cluster singleton" should {
    "not have a singleton actor running when only 1 node is running" in new Context {
      val cluster: Seq[ActorSystem] = cluster(1)

      cluster.zipWithIndex foreach { case (actorSystem, index) =>
        actorSystem.actorOf(PingActor.props(actorSystem, index + 1), "ping-actor")
      }

      // With only 1 node running, and configured to need at least 2 to form a cluster.
      expectNoMsg(10 seconds)
    }

    "run singleton actor for 2 running nodes" in new Context {
      val cluster: Seq[ActorSystem] = cluster(2)

      cluster.zipWithIndex foreach { case (actorSystem, index) =>
        actorSystem.actorOf(PingActor.props(actorSystem, index + 1), "ping-actor")
      }

      // With 2 nodes running, a singleton actor can be pinged.
      eventually(retries = 10, sleep = 3 seconds) {
        info(s"Pinging.....")
        cluster.head.actorSelection("akka://my-actor-system/user/ping-actor/singleton") ! Ping
        expectMsgType[Pong]
      }
    }

    "run singleton actor for 2 running nodes - using distributed pub/sub" in new Context {
      case object ActorRunning

      val cluster: Seq[ActorSystem] = cluster(2)

      cluster.zipWithIndex foreach { case (actorSystem, index) =>
        actorSystem.actorOf(PingActor.props(actorSystem, index + 1), "ping-actor")
      }

      // With 2 nodes running, a singleton actor can be pinged by publishing to a known "topic".
      eventually(retries = 10, sleep = 3 seconds) {
        cluster.head actorOf Props {
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
      val cluster: Seq[ActorSystem] = cluster(2, ConfigFactory.parseString("akka.cluster.auto-down-unreachable-after = 30s"))

      cluster.zipWithIndex foreach { case (actorSystem, index) =>
        actorSystem.actorOf(PingActor.props(actorSystem, index + 1), "ping-actor")
      }

      // With 2 nodes running, a singleton actor can be pinged.
      eventually(retries = 10, sleep = 3 seconds) {
        info(s"Pinging.....")
        cluster.head.actorSelection("akka://my-actor-system/user/ping-actor/singleton") ! Ping
        expectMsgType[Pong]
      }

      // 1 node leaves the cluster.
      cluster.head.eventStream.subscribe(self, classOf[MemberEvent])

      Cluster(cluster.head).down(Cluster(cluster.head).selfAddress)

      eventuallyExpectMsg[MemberRemoved] {
        case MemberRemoved(_, _) => ok
      }

      // With only 1 node running, and configured to need at least 2 to form a cluster.
      val depletedCluster = cluster.tail
      depletedCluster.head.actorSelection("akka://my-actor-system/user/ping-actor/singleton") ! Ping

      expectMsgPF(30 seconds) {
        case Pong(_, _) => ko
        case _ => ok
      }
    }

    "run singleton actor for 3 running nodes, even after bringing down the leading node" in new Context {
      val cluster: Seq[ActorSystem] = cluster(3, ConfigFactory.parseString("akka.cluster.auto-down-unreachable-after = 1s"))

      cluster.zipWithIndex foreach { case (actorSystem, index) =>
        actorSystem.actorOf(PingActor.props(actorSystem, index + 1), "ping-actor")
      }

      // With 3 nodes running, a singleton actor can be pinged.
      eventually(retries = 10, sleep = 3 seconds) {
        info(s"Pinging.....")
        cluster.head.actorSelection("akka://my-actor-system/user/ping-actor/singleton") ! Ping
        expectMsgType[Pong]
      }

      // 1 node leaves the cluster.
      cluster.head.eventStream.subscribe(self, classOf[MemberEvent])

      Cluster(cluster.head).down(Cluster(cluster.head).selfAddress)

      eventuallyExpectMsg[MemberRemoved] {
        case MemberRemoved(_, _) => ok
      }

      // With 2 nodes running, a singleton actor can be pinged.
      val depletedCluster = cluster.tail

      eventually(retries = 10, sleep = 3 seconds) {
        info(s"Pinging.....")
        depletedCluster.head.actorSelection("akka://my-actor-system/user/ping-actor/singleton") ! Ping
        expectMsgType[Pong]
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
    settings = ClusterSingletonManagerSettings(system)/*.withRole("my-service")*/
  )
}

class PingActor(id: Int) extends Actor {
  import PingActor._

  val mediator = DistributedPubSub(context.system).mediator

  // Subscribe to the topic named "content"
  mediator ! Subscribe("content", self)

  override def receive: Receive = {
    case Ping =>
      println(s"===> Ponging from actor with ID $id")
      sender() ! Pong(self.path, id)
  }
}