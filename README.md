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

To actually run the application, first "assemble" it:
> sbt assembly

This packages up an executable JAR - Note that "assembly" will first compile and test.

Then just run as any executable JAR, with any extra Java options for overriding configurations.

For example, to use a config file (other than the default application.conf) which is located on the file system (in this case in the boot directory)
> java -Dconfig.file=test-classes/my-application.conf -jar <jar name>.jar

And other examples:

booting from project root:
> java -Dspray.can.server.port=8080 -jar target/scala-2.11/<jar name>.jar

and running from directory of the executable JAR using a config that is within said JAR:
> java -Dconfig.resource=application.uat.conf -jar <jar name>.jar

Finally you can perform a quick test of the application by calling the API e.g. making a cURL call to the application:
> curl http://localhost:9100/app-name 

Configuration
-------------
TODO

The project utilises Artifactory to resolve in-house modules. Do the following:
1. Copy the .credentials file into your <home directory>/.ivy2/
2. Edit this .credentials file to fill in the artifactory security credentials (amend the realm name and host where necessary)

> sbt publish

Note that initially this project refers to some libraries held within a private Artifactory. However, those libraries have been open sourced under https://github.com/UKHomeOffice.

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
     pathPrefix("example") {
       pathEndOrSingleSlash {
         get {
           complete { JObject("status" -> JString("Congratulations")) }
         }
       }
     }
  }
  
  object ExampleRouting2 extends ExampleRouting2
    
  trait ExampleRouting2 extends Routing {
   val route =
     pathPrefix("example") {
       pathEndOrSingleSlash {
         get {
           complete { JObject("status" -> JString("Congratulations")) }
         }
       }
     }
  }
```

- Create your application (App) utilitising your routings (as well as anything else e.g. booting/wiring Akka actors):
```scala
  object ExampleBoot extends App with SprayBoot {
    bootRoutings(ExampleRouting1 ~ ExampleRouting2)
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