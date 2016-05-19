package uk.gov.homeoffice.akka

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.{MILLISECONDS => _, _}
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
import de.flapdoodle.embed.process.runtime.Network._

class ClusterSingletonSpec extends Specification {
  case object ActorRunning

  "Cluster singleton" should {
    "not start singleton actor for only 1 node running" in new ActorSystemContext with ClusterSingleton {
      system2.terminate()
      system3.terminate()

      system1 actorOf Props {
        new Actor {
          override def preStart(): Unit = Cluster(context.system).subscribe(self, classOf[MemberJoined])

          override def receive: Receive = {
            case j: MemberJoined => testActor ! j
          }
        }
      }

      expectMsgType[MemberJoined](10 seconds)

      system1 actorOf Props {
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

    "run singleton actor for 2 running nodes" in new ActorSystemContext with ClusterSingleton {
      system3.terminate()

      system1 actorOf Props {
        new Actor {
          override def preStart(): Unit = Cluster(context.system).subscribe(self, classOf[MemberUp])

          override def receive: Receive = {
            case u: MemberUp => testActor ! u
          }
        }
      }

      expectMsgType[MemberUp](10 seconds)
      expectMsgType[MemberUp](10 seconds)

      TimeUnit.SECONDS.sleep(20) // TODO SORT OUT - obviously memberup doesn't count... how do we know the cluster is ready???

      system1 actorOf Props {
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

trait ClusterSingleton {
  val (system1, system2, system3) = cluster

  def cluster: (ActorSystem, ActorSystem, ActorSystem) = try {
    val (port1, port2, port3) = (freePort, freePort, freePort)

    val config: Config = load(parseString(s"""
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
          "akka.tcp://my-actor-system@127.0.0.1:$port1",
          "akka.tcp://my-actor-system@127.0.0.1:$port2",
          "akka.tcp://my-actor-system@127.0.0.1:$port3"
        ]

        roles = ["my-service"]
        min-nr-of-members = 2
        auto-down-unreachable-after = 30s
      }

      extensions = ["akka.cluster.pubsub.DistributedPubSub"]
    }"""))

    val system1 = ActorSystem("my-actor-system", ConfigFactory.parseString(s"akka.remote.netty.tcp.port = $port1").withFallback(config))
    system1.actorOf(pingActorProps(system1), "ping-actor")

    val system2 = ActorSystem("my-actor-system", ConfigFactory.parseString(s"akka.remote.netty.tcp.port = $port2").withFallback(config))
    system2.actorOf(pingActorProps(system2), "ping-actor")

    val system3 = ActorSystem("my-actor-system", ConfigFactory.parseString(s"akka.remote.netty.tcp.port = $port3").withFallback(config))
    system3.actorOf(pingActorProps(system3), "ping-actor")

    println(s"===> PORTS: $port1, $port2, $port3")

    (system1, system2, system3)
  } catch {
    case t: Throwable =>
      println(s"Failed to start up the cluster because of: ${t.getMessage}... trying again")
      cluster
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

  def pingActorProps(system: ActorSystem) = ClusterSingletonManager.props(
    singletonProps = Props(new PingActor),
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system).withRole("my-service"))
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