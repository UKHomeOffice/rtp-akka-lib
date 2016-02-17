package uk.gov.homeoffice.akka

import akka.actor.{Actor, Props}
import org.specs2.mutable.Specification

class ActorSystemSpec extends Specification with ActorSystemSpecification {
  case object ReactTo

  case object Reacted

  case class AddToState(x: Int)

  case class State(xs: Vector[Int])

  class TestActor extends Actor {
    var state = Vector.empty[Int]

    override def receive: Receive = {
      case ReactTo =>
        sender() ! Reacted

      case AddToState(x) =>
        state = state :+ x
        sender() ! State(state)

      case _ =>
    }
  }

  "Actor system" should {
    "be available" in {
      system actorOf Props { new TestActor }
      ok
    }
  }

  "Actor" should {
    "not response" in {
      val actor = system actorOf Props { new TestActor }

      actor ! "Ignore"
      expectNoMsg
    }

    "response" in {
      val actor = system actorOf Props { new TestActor }

      actor ! ReactTo
      expectMsg(Reacted)
    }
  }

  "Actor state for anonymous actor" should {
    "be unique for an example" in {
      val actor = system actorOf Props { new TestActor }

      actor ! AddToState(1)
      expectMsg(State(Vector(1)))
    }

    "be unique for another example" in {
      val actor = system actorOf Props { new TestActor }

      actor ! AddToState(2)
      expectMsg(State(Vector(2)))
    }
  }

  "Actor state for named actor" should {
    val actor = system.actorOf(Props(new TestActor), "named")

    "not be unique for an example" in {
      actor ! AddToState(1)
      expectMsg(State(Vector(1)))
    }

    "not be unique for another example" in {
      actor ! AddToState(2)
      expectMsg(State(Vector(1, 2)))
    }
  }
}