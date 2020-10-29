Akka - Reusable functionality
=============================
Akka reusable functionality and Scala Spray functionality/template for general use.

Project built with the following (main) technologies:

- Scala

- SBT

- Akka

- Akka-http

- Specs2

Introduction
------------
Boot a microservice utilising functionality built on top of Spray.

Create an application and include "routes" to expose an API to access via HTTP.

Build and Deploy
----------------
The project is built with SBT. On a Mac (sorry everyone else) do:
> brew install sbt

It is also a good idea to install Typesafe Activator (which sits on top of SBT) for when you need to create new projects - it also has some SBT extras, so running an application with Activator instead of SBT can be useful. On Mac do:
> brew install typesafe-activator

To compile:
> sbt compile

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


SBT - Revolver
--------------
sbt-revolver is a plugin for SBT enabling a super-fast development turnaround for your Scala applications:

See https://github.com/spray/sbt-revolver

For development, you can use ~re-start to go into "triggered restart" mode.
Your application starts up and SBT watches for changes in your source (or resource) files.
If a change is detected SBT recompiles the required classes and sbt-revolver automatically restarts your application. 
When you press &lt;ENTER&gt; SBT leaves "triggered restart" and returns to the normal prompt keeping your application running.
