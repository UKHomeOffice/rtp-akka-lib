package uk.gov.homeoffice.akka

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.{MILLISECONDS => _, _}
import scala.concurrent.duration._
import akka.actor.{Actor, ActorPath, ActorSystem, PoisonPill, Props, Terminated}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.event.Logging.LogEvent
import com.typesafe.config.ConfigFactory._
import com.typesafe.config.{Config, ConfigFactory}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import de.flapdoodle.embed.process.runtime.Network._

class ClusterSingletonSpec(implicit ev: ExecutionEnv) extends Specification {
  import PingActor._

  case object ActorRunning

  "Cluster singleton" should {
    "not have a singleton actor running when only 1 node is running" in new ActorSystemContext with ClusterSingleton {
      val cluster: Seq[ActorSystem] = cluster(1)

      cluster.zipWithIndex foreach { case (actorSystem, index) =>
        actorSystem.actorOf(PingActor.props(actorSystem, index + 1), s"ping-actor")
      }

      expectNoMsg()
    }

    "run singleton actor for 2 running nodes" in new ActorSystemContext with ActorExpectations with ClusterSingleton {
      val cluster: Seq[ActorSystem] = cluster(2)

      cluster.zipWithIndex foreach { case (actorSystem, index) =>
        actorSystem.actorOf(PingActor.props(actorSystem, index + 1), s"ping-actor")
      }

      cluster.head.eventStream.subscribe(self, classOf[LogEvent])

      eventuallyExpectMsg[LogEvent] {
        case l: LogEvent => l.message.toString.contains("Start -> Oldest")
      }

      cluster.head.actorSelection("akka://my-actor-system/user/ping-actor/singleton") ! Ping

      expectMsgType[Pong](10 seconds)
    }

    "run singleton actor for 2 running nodes - using distributed pub/sub" in new ActorSystemContext with ActorExpectations with ClusterSingleton {
      val cluster: Seq[ActorSystem] = cluster(2)

      cluster.zipWithIndex foreach { case (actorSystem, index) =>
        actorSystem.actorOf(PingActor.props(actorSystem, index + 1), s"ping-actor")
      }

      cluster.head.eventStream.subscribe(self, classOf[LogEvent])

      eventuallyExpectMsg[LogEvent] {
        case l: LogEvent => l.message.toString.contains("Start -> Oldest")
      }

      cluster.head actorOf Props {
        new Actor {
          override def preStart(): Unit = {
            val mediator = DistributedPubSub(context.system).mediator
            mediator ! Publish("content", Ping)
          }

          override def receive: Receive = {
            case Pong(actorPath, id) =>
              println(s"===> Got back pong from $actorPath with ID $id")
              testActor ! ActorRunning
          }
        }
      }

      expectMsgType[ActorRunning.type](10 seconds)
    }

    "run singleton actor for 3 running nodes, even after bringing down the leading node" in new ActorSystemContext with ActorExpectations with ClusterSingleton {
      val cluster: Seq[ActorSystem] = cluster(2)

      cluster.zipWithIndex foreach { case (actorSystem, index) =>
        actorSystem.actorOf(PingActor.props(actorSystem, index + 1), s"ping-actor")
      }

      cluster.head.eventStream.subscribe(self, classOf[LogEvent])

      eventuallyExpectMsg[LogEvent] {
        case l: LogEvent => l.message.toString.contains("Start -> Oldest")
      }

      cluster.head.actorSelection("akka://my-actor-system/user/ping-actor/singleton") ! Ping

      expectMsgType[Pong](10 seconds)

      cluster.head.terminate() must beLike {
        case Terminated => ok
      }.awaitFor(10 seconds)

      //val depletedCluster = cluster.tail
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