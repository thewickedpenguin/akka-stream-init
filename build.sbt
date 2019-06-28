import Dependencies._

ThisBuild / scalaVersion     := "2.12.0"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "org.github.csng"
ThisBuild / organizationName := "csng"

lazy val root = (project in file("."))
  .settings(
    name := "akka-stream-init",
    libraryDependencies ++= Seq(
      scalaTest % Test,
      "com.typesafe.akka" %% "akka-stream" % "2.5.23",
      "com.softwaremill.sttp" %% "core" % "1.6.0"
    )
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
