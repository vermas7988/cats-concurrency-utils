import Dependencies.*

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.15"

ThisBuild / organization := "dev.sachin"
ThisBuild / organizationName := "Sachin"

ThisBuild / evictionErrorLevel := Level.Warn

resolvers ++= Resolver.sonatypeOssRepos("snapshots")

lazy val root = (project in file("."))
  .settings(
    name := "cats-request-registry"
  )
  .settings(
    name := "fetch-api",
    scalacOptions ++= List("-Ymacro-annotations", "-Yrangepos", "-Wconf:cat=unused:info"),
    resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
    libraryDependencies ++= Seq(
      CompilerPlugins.betterMonadicFor,
      CompilerPlugins.kindProjector,
      Libraries.cats,
      Libraries.catsEffect
    )
  )

addCommandAlias("fmt", ";tpolecatCiMode;scalafmtSbt;scalafmtAll")
addCommandAlias("fmtCheck", ";tpolecatCiMode;scalafmtCheckAll")
addCommandAlias("c", "compile")
