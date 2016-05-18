Akka - Reusable functionality
=============================
Akka reusable functionality and Scala Spray functionality/template for general use.

Project built with the following (main) technologies:

- Scala

- SBT

- Akka

- Spray

- Specs2

Introduction
------------
Boot a microservice utilising functionality built on top of Spray.

Create an application and include "routes" to expose an API to access via HTTP.
Build up your own routes, noting that "service-statistics" route is automatically exposed for you and can be accessed as (for example):
```bash
http://localhost:9100/service-statistics
```
which would give you something like:
```javascript
{
  statistics: {
    uptime: "36663930295 nanoseconds"
    total-requests: "2"
    open-requests: "1"
    maximum-open-requests: "1"
    total-connections: "1"
    open-connections: "1"
    max-open-connections: "1"
    request-timeouts: "0"
  }
}
```

Build and Deploy
----------------
The project is built with SBT. On a Mac (sorry everyone else) do:
> brew install sbt

It is also a good idea to install Typesafe Activator (which sits on top of SBT) for when you need to create new projects - it also has some SBT extras, so running an application with Activator instead of SBT can be useful. On Mac do:
> brew install typesafe-activator

To compile:
> sbt compile

or
> activator compile

To run the specs:
> sbt test

To run integration specs:
> sbt it:test

To run integration specs:
> sbt it:test 

Configuration
-------------
TODO

The project utilises Artifactory to resolve in-house modules. Do the following:
1. Copy the .credentials file into your <home directory>/.ivy2/
2. Edit this .credentials file to fill in the artifactory security credentials (amend the realm name and host where necessary)

> sbt publish

Note that initially this project refers to some libraries held within a private Artifactory. However, those libraries have been open sourced under https://github.com/UKHomeOffice.

Testing
-------
Note regarding testing of application that utilises Spray.
At the time of writing this, Spray was in maintenance mode because of its migration to Akka HTTP.
Unfortunately, Spray uses a now out of date Specs2 library. This can be resolved by adding the following class into the package "spray.testkit" within the "test" directory of your application:
```scala
package spray.testkit

import org.specs2.execute.{ Failure, FailureException }
import org.specs2.specification.core.{ Fragments, SpecificationStructure }
import org.specs2.specification.create.DefaultFragmentFactory

/**
 * Spray's built-in support for specs2 is built against specs2 2.x, not 3.x.
 * So you cannot use the Specs2Interface from spray but need to compile one yourself (against specs2 3.x).
 * That is what this code does, taken from https://gist.github.com/gmalouf/51a8722b50f6a9d30404
 * Note that the build has to exclude Specs2 as a transitive dependency from the Spray testkit.
 */
trait Specs2Interface extends TestFrameworkInterface with SpecificationStructure {
  def failTest(msg: String) = {
    val trace = new Exception().getStackTrace.toList
    val fixedTrace = trace.drop(trace.indexWhere(_.getClassName.startsWith("org.specs2")) - 1)
    throw new FailureException(Failure(msg, stackTrace = fixedTrace))
  }

  override def map(fs: ⇒ Fragments) = super.map(fs).append(DefaultFragmentFactory.step(cleanUp()))
}

trait NoAutoHtmlLinkFragments extends org.specs2.specification.dsl.ReferenceDsl {
  override def linkFragment(alias: String) = super.linkFragment(alias)

  override def seeFragment(alias: String) = super.seeFragment(alias)
}
```

SBT - Revolver
--------------
sbt-revolver is a plugin for SBT enabling a super-fast development turnaround for your Scala applications:

See https://github.com/spray/sbt-revolver

For development, you can use ~re-start to go into "triggered restart" mode.
Your application starts up and SBT watches for changes in your source (or resource) files.
If a change is detected SBT recompiles the required classes and sbt-revolver automatically restarts your application. 
When you press &lt;ENTER&gt; SBT leaves "triggered restart" and returns to the normal prompt keeping your application running.

Example Usage
-------------
- Actor scheduling:
```scala
  class SchedulerSpec extends Specification {
    "Actor" should {
      "be scheduled to act as a poller" in new ActorSystemContext {
        val exampleSchedulerActor = system.actorOf(Props(new ExampleSchedulerActor), "exampleSchedulerActor")
        exampleSchedulerActor ! Scheduled
        expectMsg(Scheduled)
      }
  
      "not be scheduled to act as a poller" in new ActorSystemContext {
        val exampleSchedulerActor = system.actorOf(Props(new ExampleSchedulerActor with NoSchedule), "exampleNoSchedulerActor")
        exampleSchedulerActor ! Scheduled
        expectMsg(NotScheduled)
      }
    }
  }
  
  class ExampleSchedulerActor extends Actor with Scheduler {
    val schedule: Cancellable = schedule(initialDelay = 1 second, interval = 5 seconds, receiver = self, message = Wakeup)
  
    def receive = LoggingReceive {
      case Wakeup => println("Hello World!")
    }
  }
```

- Create some Spray routings - HTTP contract/gateway to your microservice:
```scala
  object ExampleRouting1 extends ExampleRouting1
  
  trait ExampleRouting1 extends Routing {
   val route =
     pathPrefix("example1") {
       pathEndOrSingleSlash {
         get {
           complete { JObject("status" -> JString("Congratulations 1")) }
         }
       }
     }
  }
  
  object ExampleRouting2 extends ExampleRouting2
    
  trait ExampleRouting2 extends Routing {
   val route =
     pathPrefix("example2") {
       pathEndOrSingleSlash {
         get {
           complete { JObject("status" -> JString("Congratulations 2")) }
         }
       }
     }
  }
```

- Create your application (App) utilitising your routings (as well as anything else e.g. booting/wiring Akka actors):
```scala
  object ExampleBoot extends App with SprayBoot with ExampleConfig {
    // You must provide an ActorSystem for Spray.
    implicit lazy val spraySystem = ActorSystem("example-boot-actor-system")
  
    bootRoutings(ExampleRouting1 ~ ExampleRouting2 ~ ExampleRoutingError)(FailureHandling.exceptionHandler)
  }
```

Noting that a "configuration" such as application.conf must be provided e.g.
```scala
  spray.can.server {
    name = "example-spray-can"
    host = "0.0.0.0"
    port = 9100
    request-timeout = 1s
    service = "example-http-routing-service"
    remote-address-header = on
  }
```

To run ExampleBoot:
```bash
sbt test:run
```

Akka Clustering
---------------
Cluster Singleton:

Actors can be managed in a cluster to run as a singleton - an actor will be distributed on multiple nodes, but only one will be running. Before looking at the necessary configuration, a quick note on metrics - these can be gathered by sigar where we have have to stipulate the "Java library path" to the necessary OS specific files. When running an application from a JAR, we can do:
```bash
java -Djava.library.path=./sigar
```

Note that without setting the "Java library path" an exception (that can be ignored) occurs.

sigar is only used for metrics but is not needed. See https://support.hyperic.com

Your application.conf for a Cluster Singleton, can use the following template:
```json
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
      "akka.tcp://your-actor-system@127.0.0.1:2551",
      "akka.tcp://your-actor-system@127.0.0.1:2552",
      "akka.tcp://your-actor-system@127.0.0.1:2553"
    ]

    roles = ["your-service"]
    min-nr-of-members = 2
    auto-down-unreachable-after = 30s
  }
}  
```

Each node that starts up on the same box would need a different port e.g. 2551, 2552 etc.
In production, the nodes would be on different boxes and so can all have the same ports and said port could then also be declared for akka.actor.remote.netty.tcp.port.

To start your application from a JAR and stipulate the boot (main) class, when needing to override akka.actor.remote.netty.tcp.port:
```bash
java -Djava.library.path=./sigar -cp application.jar blah.MainStipulatingPortNumber