package uk.gov.homeoffice.akka.cluster

import java.io.{BufferedWriter, File, FileWriter}
import java.util.concurrent.TimeUnit
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import uk.gov.homeoffice.io.Network

/**
  * Example of clustering using ClusterActorSystem a simple wrapper as an entry point to the powerful Akka clustering.
  * To run:
  * {{{
  *   > sbt test:run
  *   Multiple main classes detected, select one to run:
  *   [1] uk.gov.homeoffice.akka.cluster.ClusterActorSystemExampleApp
  *   [2] uk.gov.homeoffice.spray.ExampleBoot
  *   ^JEnter number:
  * }}}
  * ... select 1 and enter
  */
object ClusterActorSystemExampleApp extends App with Network {
  withConfig {
    // Imagine we are starting up 3 nodes on 3 separate boxes (here we will have simply utilise 3 separately configured ports).
    val actorSystem1 = ClusterActorSystem(1)
    val actorSystem2 = ClusterActorSystem(2)
    val actorSystem3 = ClusterActorSystem(3)

    /*
    The above will produce log out like the following (trimmed down for brevity)

    uk.gov.homeoffice.akka.cluster.ClusterActorSystem - Configuring cluster seed node 1 as 127.0.0.1:62152 in cluster 'cluster-actor-system'
    uk.gov.homeoffice.akka.cluster.ClusterActorSystem - Configuring cluster seed node 2 as 127.0.0.1:62153 in cluster 'cluster-actor-system'
    uk.gov.homeoffice.akka.cluster.ClusterActorSystem - Configuring cluster seed node 3 as 127.0.0.1:62154 in cluster 'cluster-actor-system'

    uk.gov.homeoffice.akka.cluster.ClusterActorSystem - Booting Cluster actor system node 1 on 127.0.0.1:62152 in cluster "cluster-actor-system"
    akka.cluster.Cluster(akka://cluster-actor-system) Cluster Node [akka.tcp://cluster-actor-system@127.0.0.1:62152] - Started up successfully

    uk.gov.homeoffice.akka.cluster.ClusterActorSystem - Booting Cluster actor system node 2 on 127.0.0.1:62153 in cluster "cluster-actor-system"
    akka.cluster.Cluster(akka://cluster-actor-system) Cluster Node [akka.tcp://cluster-actor-system@127.0.0.1:62153] - Started up successfully

    uk.gov.homeoffice.akka.cluster.ClusterActorSystem - Booting Cluster actor system node 3 on 127.0.0.1:62154 in cluster "cluster-actor-system"
    akka.cluster.Cluster(akka://cluster-actor-system) Cluster Node [akka.tcp://cluster-actor-system@127.0.0.1:62154] - Started up successfully

    akka.cluster.Cluster(akka://cluster-actor-system) Cluster Node [akka.tcp://cluster-actor-system@127.0.0.1:62152] - Leader is moving node [akka.tcp://cluster-actor-system@127.0.0.1:62152] to [Up]
    */

    sys addShutdownHook {
      println("Shutting down cluster...")
      actorSystem1.terminate
      actorSystem2.terminate
      actorSystem3.terminate
    }

    // Let's start up an actor and fire a message to it. If the message is received, log it and stop the application.
    TimeUnit.SECONDS.sleep(20) // Rubbish, but wait a while, to allow reading of console logs, before continuing (Hey! It's just a simple example).

    testActor(actorSystem1) ! "Nice clustering!"
  }


  def withConfig(application: => Any) = {
    freeport() { clusterPort1 =>
      freeport() { clusterPort2 =>
        freeport() { clusterPort3 =>
          val allThatIsRequiredFromYourConfig = s"""
            akka {
              cluster {
                seed-nodes = [{
                  host = "127.0.0.1"
                  port = $clusterPort1
                }, {
                  host = "127.0.0.1"
                  port = $clusterPort2
                }, {
                  host = "127.0.0.1"
                  port = $clusterPort3
                }]
              }
            }"""

          val configFile = File.createTempFile("application", ".conf")
          configFile.deleteOnExit()
          System.clearProperty("config.resource") // Avoid clashing this example's temporary config with a build config.
          System.setProperty("config.file", configFile.getCanonicalFile.toString) // Set config location to be picked up by our custom library code.

          val bw = new BufferedWriter(new FileWriter(configFile))
          bw.write(allThatIsRequiredFromYourConfig)
          bw.close()

          application
        }
      }
    }
  }

  def testActor(system: ActorSystem): ActorRef = system actorOf {
    Props {
      new Actor() {
        def receive: Receive = {
          case x =>
            println(s"Successful test, actor received: '$x'... going to shutdown.")
            sys.exit
        }
      }
    }
  }
}