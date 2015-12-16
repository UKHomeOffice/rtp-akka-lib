import sbt._
import Keys._
import spray.revolver.RevolverPlugin._

object Build extends Build {
  val moduleName = "rtp-akka-lib"

  lazy val akka = Project(id = moduleName, base = file("."))
    .configs(IntegrationTest)
    .settings(Revolver.settings)
    .settings(Defaults.itSettings: _*)
    .settings(
      name := moduleName,
      organization := "uk.gov.homeoffice",
      version := "1.2.0-SNAPSHOT",
      scalaVersion := "2.11.7",
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
        "Kamon Repository" at "http://repo.kamon.io"),
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.4.0" withSources(),
        "com.typesafe.akka" %% "akka-remote" % "2.4.0" withSources(),
        "com.typesafe.akka" %% "akka-slf4j" % "2.4.0" withSources(),
        "io.spray" %% "spray-can" % "1.3.3" withSources() excludeAll ExclusionRule(organization = "org.json4s"),
        "io.spray" %% "spray-routing" % "1.3.3" withSources() excludeAll ExclusionRule(organization = "org.json4s")),
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-testkit" % "2.4.0" % Test withSources(),
        "io.spray" %% "spray-testkit" % "1.3.3" % Test withSources() excludeAll (ExclusionRule(organization = "org.specs2"), ExclusionRule(organization = "org.json4s"))
      )
    )

  val testPath = "../rtp-test-lib"
  val ioPath = "../rtp-io-lib"

  val root = if (file(testPath).exists && sys.props.get("jenkins").isEmpty) {
    println("=====================")
    println("Build Locally domain ")
    println("=====================")

    val testLib = ProjectRef(file(testPath), "rtp-test-lib")
    val ioLib = ProjectRef(file(ioPath), "rtp-io-lib")

    akka.dependsOn(testLib % "test->test;compile->compile")
        .dependsOn(ioLib % "test->test;compile->compile")
  } else {
    println("========================")
    println("Build on Jenkins domain ")
    println("========================")

    akka.settings(
      libraryDependencies ++= Seq(
        "uk.gov.homeoffice" %% "rtp-test-lib" % "1.0" withSources(),
        "uk.gov.homeoffice" %% "rtp-test-lib" % "1.0" % Test classifier "tests" withSources(),
        "uk.gov.homeoffice" %% "rtp-io-lib" % "1.0" withSources(),
        "uk.gov.homeoffice" %% "rtp-io-lib" % "1.0" % Test classifier "tests" withSources()
      ))
  }
}
