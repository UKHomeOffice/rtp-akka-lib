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
    /*"not start singleton actor for only 1 node running" in new ActorSystemContext with ClusterSingleton {
      val cluster: Seq[ActorSystem] = cluster(1)

      cluster.head actorOf Props {
        new Actor {
          override def preStart(): Unit = Cluster(context.system).subscribe(self, classOf[MemberJoined])

          override def receive: Receive = {
            case j: MemberJoined => testActor ! j
          }
        }
      }

      expectMsgType[MemberJoined](10 seconds)

      cluster.head actorOf Props {
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
    }*/

    "run singleton actor for 2 running nodes" in new ActorSystemContext with ClusterSingleton {
      val cluster: Seq[ActorSystem] = cluster(2)

      cluster.head actorOf Props {
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

      cluster.head actorOf Props {
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
  def cluster(numberOfNodes: Int): Seq[ActorSystem] = try {
    val ports = 1 to numberOfNodes map { _ => freePort }

    val seedNodes = ports map { port =>
      s""""akka.tcp://my-actor-system@127.0.0.1:$port""""
    } mkString ", "

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
          seed-nodes = [ $seedNodes ]
          roles = ["my-service"]
          min-nr-of-members = 2
          auto-down-unreachable-after = 30s
        }

        extensions = ["akka.cluster.pubsub.DistributedPubSub"]
      }"""))

    ports map { port =>
      val actorSystem = ActorSystem("my-actor-system", ConfigFactory.parseString(s"akka.remote.netty.tcp.port = $port").withFallback(config))
      actorSystem.actorOf(pingActorProps(actorSystem), "ping-actor")

      actorSystem
    }
  } catch {
    case t: Throwable =>
      println(s"Error in starting up an actor system because of: ${t.getMessage}... Will try again")
      TimeUnit.SECONDS.sleep(1)
      cluster(numberOfNodes)
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

  def clusterConfig(ports: Seq[Int]): Config = load(parseString(s"""
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
        seed-nodes = [ "${ports map { port => s"akka.tcp://my-actor-system@127.0.0.1:$port" } mkString ", "}" ]
        roles = ["my-service"]
        min-nr-of-members = 2
        auto-down-unreachable-after = 30s
      }

      extensions = ["akka.cluster.pubsub.DistributedPubSub"]
    }"""))

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