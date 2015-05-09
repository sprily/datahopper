import sbt.Project.projectToRef

lazy val commonSettings = Seq(
  organization := "uk.co.sprily",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.6",
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-Xlint",
    "-Ywarn-unused-import",
    "-unchecked"),
  libraryDependencies ++= commonDependencies,
  resolvers ++= commonResolvers
)

lazy val commonResolvers = Seq(
  "scalaz-bintray"         at "http://dl.bintray.com/scalaz/releases",
  "Sprily 3rd Party"       at "https://repo.sprily.co.uk/nexus/content/repositories/thirdparty"
)

lazy val commonDependencies = Seq(

  // logging
  "com.typesafe.scala-logging"  %% "scala-logging"        % "3.1.0",
  "ch.qos.logback"               % "logback-core"         % "1.1.2",
  "ch.qos.logback"               % "logback-classic"      % "1.1.2",

  // testing
  "org.specs2"                  %% "specs2-core"          % "3.6"         % "test",
  "org.specs2"                  %% "specs2-junit"         % "3.6"         % "test"
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
  )

lazy val modbus = (project in file("modbus")).
  settings(commonSettings: _*).
  settings(
    name := "dh-modbus",
    libraryDependencies ++= Seq(
      "uk.co.sprily"       % "com.ghgande.j2mod"   % "1.03"
    )
  ).
  dependsOn(harvester)
