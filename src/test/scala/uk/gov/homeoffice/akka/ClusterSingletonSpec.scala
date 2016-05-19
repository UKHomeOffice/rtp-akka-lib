package uk.gov.homeoffice.akka

import scala.concurrent.duration._
import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.typesafe.config.ConfigFactory._
import com.typesafe.config.{Config, ConfigFactory}
import org.specs2.mutable.Specification

class ClusterSingletonSpec extends Specification {
  case object ActorRunning

  "Cluster singleton" should {
    "not start singleton actor for only 1 node running" in new ActorSystemContext {
      val b1 = new Boot(2551)

      b1.system actorOf Props {
        new Actor {
          override def preStart(): Unit = Cluster(context.system).subscribe(self, classOf[MemberJoined])

          override def receive: Receive = {
            case j: MemberJoined => testActor ! j
          }
        }
      }

      expectMsgType[MemberJoined](10 seconds)

      b1.system actorOf Props {
        new Actor {
          override def preStart(): Unit = {
            val mediator = DistributedPubSub(context.system).mediator
            mediator ! Publish("content", "ping")
          }

          override def receive: Receive = {
            case "pong" => testActor ! ActorRunning // This should not happen
          }
        }
      }

      expectNoMsg()
    }

    "run singleton actor for 2 running nodes" in new ActorSystemContext {
      val b1 = new Boot(2551)
      val b2 = new Boot(2552)

      b1.system actorOf Props {
        new Actor {
          override def preStart(): Unit = Cluster(context.system).subscribe(self, classOf[MemberUp])

          override def receive: Receive = {
            case u: MemberUp => testActor ! u
          }
        }
      }

      expectMsgType[MemberUp](10 seconds)
      expectMsgType[MemberUp](10 seconds)

      b1.system actorOf Props {
        new Actor {
          override def preStart(): Unit = {
            val mediator = DistributedPubSub(context.system).mediator
            mediator ! Publish("content", "ping")
          }

          override def receive: Receive = {
            case "pong" => testActor ! ActorRunning
          }
        }
      }


      expectMsgType[ActorRunning.type](10 seconds)
    }
  }
}

class Boot(clusterPort: Int) {
  val config: Config = load(parseString("""
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
        seed-nodes = [
          "akka.tcp://my-actor-system@127.0.0.1:2551",
          "akka.tcp://my-actor-system@127.0.0.1:2552",
          "akka.tcp://my-actor-system@127.0.0.1:2553"
        ]

        roles = ["my-service"]
        min-nr-of-members = 2
        auto-down-unreachable-after = 30s
      }

      extensions = ["akka.cluster.pubsub.DistributedPubSub"]
    }"""))

  val system = ActorSystem("my-actor-system", ConfigFactory.parseString(s"akka.remote.netty.tcp.port = $clusterPort").withFallback(config))

  val pingActorProps = ClusterSingletonManager.props(
    singletonProps = PingActor.props,
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system).withRole("my-service"))

  val pingActor = system.actorOf(pingActorProps, "ping-actor")
}

object PingActor {
  def props = Props(new PingActor)
}

class PingActor extends Actor {
  val mediator = DistributedPubSub(context.system).mediator

  // Subscribe to the topic named "content"
  mediator ! Subscribe("content", self)

  override def receive: Receive = {
    case "ping" =>
      println(s"===> Pong")
      sender() ! "pong"
  }
}