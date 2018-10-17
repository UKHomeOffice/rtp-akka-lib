import sbt._
import Keys._
import spray.revolver.RevolverPlugin._

object Build extends Build {
  val moduleName = "rtp-akka-lib"

  lazy val ItTest = config("it") extend Test

  val root = Project(id = moduleName, base = file("."))
    .configs(IntegrationTest)
    .settings(Defaults.itSettings: _*)
    .configs(ItTest)
    .settings(inConfig(ItTest)(Defaults.testSettings) : _*)
    .settings(Revolver.settings)
    .settings(javaOptions in Test += "-Dconfig.resource=application.test.conf")
    .settings(
      name := moduleName,
      organization := "uk.gov.homeoffice",
      scalaVersion := "2.11.8",
      scalacOptions ++= Seq(
        "-feature",
        "-language:implicitConversions",
        "-language:higherKinds",
        "-language:existentials",
        "-language:reflectiveCalls",
        "-language:postfixOps",
        "-Yrangepos",
        "-Yrepl-sync"),
      ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
      resolvers ++= Seq(
        "Artifactory Snapshot Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-snapshot-local/",
        "Artifactory Release Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-release-local/",
        "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
        "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
        "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
        "Kamon Repository" at "http://repo.kamon.io"
      )
    )
    .settings(libraryDependencies ++= {
      val `akka-version` = "2.4.16"
      val `spray-version` = "1.3.3"
      val `rtp-io-lib-version` = "1.9.12"
      val `rtp-test-lib-version` = "1.4.8"

      Seq(
        "com.typesafe.akka" %% "akka-actor" % `akka-version` withSources(),
        "com.typesafe.akka" %% "akka-remote" % `akka-version` withSources(),
        "com.typesafe.akka" %% "akka-cluster-tools" % `akka-version` withSources(),
        "com.typesafe.akka" %% "akka-cluster-metrics" % `akka-version` withSources(),
        "com.typesafe.akka" %% "akka-stream" % `akka-version` withSources(),
        "com.typesafe.akka" %% "akka-slf4j" % `akka-version` withSources(),
        "io.spray" %% "spray-can" % `spray-version` withSources() excludeAll ExclusionRule(organization = "org.json4s") exclude("io.spray", "spray-routing"),
        "io.spray" %% "spray-routing-shapeless2" % `spray-version` withSources() excludeAll ExclusionRule(organization = "org.json4s"),
        "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "1.50.2" withSources(),
        "uk.gov.homeoffice" %% "rtp-io-lib" % `rtp-io-lib-version` withSources(),
        "uk.gov.homeoffice" %% "rtp-test-lib" % `rtp-test-lib-version` withSources(),
        "org.scala-lang.modules" %% "scala-pickling" % "0.10.1"
      ) ++ Seq(
        "com.typesafe.akka" %% "akka-testkit" % `akka-version` % "it, test" withSources(),
        "io.spray" %% "spray-testkit" % `spray-version` % "it, test" withSources() excludeAll (ExclusionRule(organization = "org.specs2"), ExclusionRule(organization = "org.json4s")) exclude("io.spray", "spray-routing"),
        "uk.gov.homeoffice" %% "rtp-io-lib" % `rtp-io-lib-version` % "it, test" classifier "tests" withSources(),
        "uk.gov.homeoffice" %% "rtp-test-lib" % `rtp-test-lib-version` % "it, test" classifier "tests" withSources()
      )
    })
}