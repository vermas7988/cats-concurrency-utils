import sbt.*

object Dependencies {

  object Versions {
    val cats       = "2.10.0"
    val catsEffect = "3.5.4"

    // are these even needed in this project?
    val betterMonadicFor = "0.3.1"
    val kindProjector    = "0.13.3"

  }

  object Libraries {
    val cats       = "org.typelevel" %% "cats-core"   % Versions.cats
    val catsEffect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
  }

  object CompilerPlugins {
    val betterMonadicFor = compilerPlugin(
      "com.olegpy" %% "better-monadic-for" % Versions.betterMonadicFor
    )
    val kindProjector = compilerPlugin(
      "org.typelevel" % "kind-projector" % Versions.kindProjector cross CrossVersion.full
    )
  }

}
