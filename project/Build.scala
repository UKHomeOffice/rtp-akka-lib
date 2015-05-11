import sbt._
import Keys._
import spray.revolver.RevolverPlugin._

object Build extends Build {
  lazy val root =
    Project(id = "akka-it", base = file("."))
      .configs(IntegrationTest)
      .settings(Revolver.settings)
      .settings(Defaults.itSettings: _*)
      .settings(
        name := "akka-it",
        organization := "uk.gov.homeoffice",
        version := "1.0-SNAPSHOT",
        scalaVersion := "2.11.6",
        scalacOptions ++= Seq(
          "-feature",
          "-language:implicitConversions",
          "-language:higherKinds",
          "-language:existentials",
          "-language:reflectiveCalls",
          "-language:postfixOps",
          "-Yrangepos"),
        ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
        resolvers ++= Seq(
          "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
          "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
          "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
          "Kamon Repository" at "http://repo.kamon.io",
          "Artifactory Snapshot Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-snapshot-local/"),
        libraryDependencies ++= Seq(
          "com.typesafe.akka" %% "akka-actor" % "2.3.9" withSources(),
          "org.scalautils" %% "scalautils" % "2.1.5" withSources(),
          "uk.gov.homeoffice" %% "io-it" % "1.0-SNAPSHOT" withSources()),
        libraryDependencies ++= Seq(
          "org.specs2" %% "specs2" % "2.4.17" % "test, it" withSources(),
          "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test, it" withSources(),
          "uk.gov.homeoffice" %% "io-it" % "1.0-SNAPSHOT" % "test, it" classifier "tests" withSources()))
}