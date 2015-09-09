import sbt.Project.projectToRef

lazy val commonSettings = Seq(
  organization := "uk.co.sprily",
  version := "0.1.3",
  scalaVersion := "2.11.6",
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-Xlint",
    "-Ywarn-unused-import",
    "-unchecked"),
  libraryDependencies ++= commonDependencies,
  resolvers ++= commonResolvers
) ++ publishingSettings

lazy val commonResolvers = Seq(
  "scalaz-bintray"         at "http://dl.bintray.com/scalaz/releases",
  "Sprily Releases"        at "https://repo.sprily.co.uk/nexus/content/repositories/releases",
  "Sprily 3rd Party"       at "https://repo.sprily.co.uk/nexus/content/repositories/thirdparty"
)

lazy val commonDependencies = Seq(

  // logging
  "com.typesafe.scala-logging"  %% "scala-logging"        % "3.1.0",
  "ch.qos.logback"               % "logback-core"         % "1.1.2",
  "ch.qos.logback"               % "logback-classic"      % "1.1.2",

  // testing
  "org.specs2"                  %% "specs2-core"          % "3.6"         % "test",
  "org.specs2"                  %% "specs2-junit"         % "3.6"         % "test",
  "org.specs2"                  %% "specs2-scalacheck"    % "3.6"         % "test"
)

lazy val publishingSettings = Seq(
  publishTo := {
    val nexus = "https://repo.sprily.co.uk/nexus/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "content/repositories/releases")
  }
)

lazy val root = (project in file (".")).
  settings(publishingSettings:_*).
  settings(
    packagedArtifacts := Map.empty
  ).aggregate(harvester, modbus, util, scheduling)

lazy val util = (project in file("util")).
  settings(commonSettings: _*).
  settings(
    name := "dh-util",
    libraryDependencies ++= Seq(
      "org.apache.commons"      % "commons-pool2"       % "2.3",
      "org.scalaz"             %% "scalaz-core"         % "7.1.1",
      "org.scalaz"             %% "scalaz-concurrent"   % "7.1.1",
      "org.scalaz.stream"      %% "scalaz-stream"       % "0.7a"
    )
  )

lazy val scheduling = (project in file("scheduling")).
  settings(commonSettings: _*).
  settings(
    name := "dh-scheduling"
  )

lazy val harvester = (project in file("harvester")).
  settings(commonSettings: _*).
  settings(
    name := "dh-harvester",
    libraryDependencies ++= Seq(
      "org.scalaz"             %% "scalaz-core"         % "7.1.1",
      "org.scalaz"             %% "scalaz-concurrent"   % "7.1.1",
      "org.scalaz.stream"      %% "scalaz-stream"       % "0.7a",
      "org.typelevel"          %% "scodec-bits"         % "1.0.4",
      "com.github.nscala-time" %% "nscala-time"         % "2.0.0"
    )
  ).
  dependsOn(scheduling)

lazy val modbus = (project in file("modbus")).
  settings(commonSettings: _*).
  settings(
    name := "dh-modbus",
    libraryDependencies ++= Seq(
      "uk.co.sprily"       % "com.ghgande.j2mod"   % "1.04"
    )
  ).
  dependsOn(harvester, util)
