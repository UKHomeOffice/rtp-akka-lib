import sbt._
import Keys._
import spray.revolver.RevolverPlugin._

object Build extends Build {
  val moduleName = "rtp-akka-lib"

  val root = Project(id = moduleName, base = file("."))
    .configs(IntegrationTest)
    .settings(Revolver.settings)
    .settings(Defaults.itSettings: _*)
    .settings(
      name := moduleName,
      organization := "uk.gov.homeoffice",
      version := "1.5.0",
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
      val `akka-version` = "2.4.0"
      val `spray-version` = "1.3.3"
      val `rtp-io-lib-version` = "1.6.0"
      val `rtp-test-lib-version` = "1.2.0"

      Seq(
        "com.typesafe.akka" %% "akka-actor" % `akka-version` withSources(),
        "com.typesafe.akka" %% "akka-remote" % `akka-version` withSources(),
        "com.typesafe.akka" %% "akka-slf4j" % `akka-version` withSources(),
        "io.spray" %% "spray-can" % `spray-version` withSources() excludeAll ExclusionRule(organization = "org.json4s"),
        "io.spray" %% "spray-routing" % `spray-version` withSources() excludeAll ExclusionRule(organization = "org.json4s"),
        "uk.gov.homeoffice" %% "rtp-io-lib" % `rtp-io-lib-version` withSources(),
        "uk.gov.homeoffice" %% "rtp-test-lib" % `rtp-test-lib-version` withSources()
      ) ++ Seq(
        "com.typesafe.akka" %% "akka-testkit" % `akka-version` % Test withSources(),
        "io.spray" %% "spray-testkit" % `spray-version` % Test withSources() excludeAll (ExclusionRule(organization = "org.specs2"), ExclusionRule(organization = "org.json4s")),
        "uk.gov.homeoffice" %% "rtp-io-lib" % `rtp-io-lib-version` % Test classifier "tests" withSources(),
        "uk.gov.homeoffice" %% "rtp-test-lib" % `rtp-test-lib-version` % Test classifier "tests" withSources()
      )
    })
}