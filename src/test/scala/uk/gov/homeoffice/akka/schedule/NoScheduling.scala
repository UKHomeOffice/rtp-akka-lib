package uk.gov.homeoffice.akka.schedule

import akka.actor.Actor

/**
 * Why provide a "no scheduling" such that a scheduling Actor will not "wake up" and perform its duties?
 * One way that this trait can be very useful, is when testing a scheduling Actor's functionality - essentially its API (which is only exposed via a message protocol).
 * When testing a scheduling actor's functionality, messages sent to it by a specification can be processed and assertions made on the outcome of said processing.
 * During a test, you would usually not want the actor to also be "woken up" by the scheduling mechanism, as this would probably interfere with the running test.
 */
trait NoScheduling {
  this: Actor with Scheduling[_] =>

  override def preStart() = ()
}