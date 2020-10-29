import sbt.{Credentials, Path, _}
import Keys._

val moduleName = "rtp-akka-lib"

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

lazy val ItTest = config("it") extend Test

val root = Project(id = moduleName, base = file("."))
  .enablePlugins(GitVersioning)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)
  .configs(ItTest)
  .settings(inConfig(ItTest)(Defaults.testSettings) : _*)
  .settings(javaOptions in Test += "-Dconfig.resource=application.test.conf")
  .settings(
    name := moduleName,
    organization := "uk.gov.homeoffice",
    scalaVersion := "2.12.6",
    scalacOptions ++= Seq(
      "-feature",
      "-language:implicitConversions",
      "-language:higherKinds",
      "-language:existentials",
      "-language:reflectiveCalls",
      "-language:postfixOps",
      "-Yrangepos",
      "-Yrepl-sync"),
    resolvers ++= Seq(
      "Artifactory Snapshot Realm" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/libs-snapshot-local/",
      "Artifactory Release Realm" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/libs-release-local/",
      "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
      "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
      "Kamon Repository" at "http://repo.kamon.io"
    )
  )
  .settings(libraryDependencies ++= {
    val `akka-version` = "2.6.8"
    val `rtp-io-lib-version` = "2.2.0"
    val `rtp-test-lib-version` = "1.6.6-g6f56307"
    val AkkaHttpVersion =  "10.2.1"

    Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % `akka-version` withSources(),
      "com.typesafe.akka" %% "akka-stream-typed" % `akka-version` withSources(),
      "com.typesafe.akka" %% "akka-slf4j" % `akka-version` withSources(),
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "1.50.2" withSources(),
      "uk.gov.homeoffice" %% "rtp-io-lib" % `rtp-io-lib-version` withSources(),
      "uk.gov.homeoffice" %% "rtp-test-lib" % `rtp-test-lib-version` withSources()//,
    ) ++ Seq(
      "com.typesafe.akka" %% "akka-testkit" % `akka-version` % "it, test" withSources(),
      "uk.gov.homeoffice" %% "rtp-io-lib" % `rtp-io-lib-version` % "it, test" classifier "tests" withSources(),
      "uk.gov.homeoffice" %% "rtp-test-lib" % `rtp-test-lib-version` % "it, test" classifier "tests" withSources()
    )
  })


publishTo := {
  val artifactory = sys.env.get("ARTIFACTORY_SERVER").getOrElse("https://artifactory.registered-traveller.homeoffice.gov.uk/")
  Some("release"  at artifactory + "artifactory/libs-release-local")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

publishArtifact in (Test, packageBin) := true
publishArtifact in (Test, packageDoc) := true
publishArtifact in (Test, packageSrc) := true

fork in run := true
fork in Test := true

git.useGitDescribe := true
git.gitDescribePatterns := Seq("v*.*")
git.gitTagToVersionNumber := { tag :String =>

val branchTag = if (git.gitCurrentBranch.value == "master") "" else "-" + git.gitCurrentBranch.value
val uncommit = if (git.gitUncommittedChanges.value) "-U" else ""

tag match {
  case v if v.matches("v\\d+.\\d+") => Some(s"$v.0${uncommit}".drop(1))
  case v if v.matches("v\\d+.\\d+-.*") => Some(s"${v.replaceFirst("-",".")}${branchTag}${uncommit}".drop(1))
  case _ => None
}}
