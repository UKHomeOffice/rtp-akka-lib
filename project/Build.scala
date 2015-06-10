import sbt._
import Keys._
import spray.revolver.RevolverPlugin._

object Build extends Build {
  val moduleName = "rtp-akka-lib"

  lazy val root = Project(id = moduleName, base = file("."))
    .configs(IntegrationTest)
    .settings(Revolver.settings)
    .settings(Defaults.itSettings: _*)
    .settings(
      name := moduleName,
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
        "-Yrangepos",
        "-Yrepl-sync"),
      ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
      resolvers ++= Seq(
        "Artifactory Snapshot Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-snapshot-local/",
        "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
        "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
        "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
        "Kamon Repository" at "http://repo.kamon.io"),
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.3.9" withSources(),
        "io.spray" %% "spray-can" % "1.3.3" withSources(),
        "io.spray" %% "spray-routing" % "1.3.3" withSources(),
        "uk.gov.homeoffice" %% "rtp-io-lib" % "1.0-SNAPSHOT" withSources()),
      libraryDependencies ++= Seq(
        "org.specs2" %% "specs2-core" % "3.6" % "test, it" withSources(),
        "org.specs2" %% "specs2-mock" % "3.6" % "test, it" withSources(),
        "org.specs2" %% "specs2-matcher-extra" % "3.6" % "test, it" withSources(),
        "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test, it" withSources(),
        "io.spray" %% "spray-testkit" % "1.3.3" % "test, it" withSources() excludeAll(
          ExclusionRule(organization = "org.specs2", name = "specs2-core_2.11"),
          ExclusionRule(organization = "org.specs2", name = "specs2-mock_2.11"),
          ExclusionRule(organization = "org.specs2", name = "specs2-matcher-extra_2.11")
        ),
        "uk.gov.homeoffice" %% "rtp-test-lib" % "1.0-SNAPSHOT" % "test, it" classifier "tests" withSources(),
        "uk.gov.homeoffice" %% "rtp-io-lib" % "1.0-SNAPSHOT" % "test, it" classifier "tests" withSources()))
}