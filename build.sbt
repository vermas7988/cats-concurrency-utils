ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

ThisBuild / organization := "dev.sachin"
ThisBuild / organizationName := "Sachin"

ThisBuild / evictionErrorLevel := Level.Warn

resolvers ++= Resolver.sonatypeOssRepos("snapshots")

lazy val root = (project in file("."))
  .settings(
    name := "cats-concurrency-utils",
    scalacOptions ++= List(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-Xfatal-warnings",
      "-Xlint",
      "-Ymacro-annotations",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Ywarn-unused:imports",
      "-Ywarn-unused:locals",
      "-Ywarn-unused:params",
      "-Ywarn-unused:patvars",
      "-Ywarn-unused:privates"
    ),
    resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "3.5.7",
      "org.scalatest" %% "scalatest"   % "3.2.19" % Test
    )
  )

addCommandAlias("fmt", ";tpolecatCiMode;scalafmtSbt;scalafmtAll")
addCommandAlias("fmtCheck", ";tpolecatCiMode;scalafmtCheckAll")
addCommandAlias("c", "compile")
