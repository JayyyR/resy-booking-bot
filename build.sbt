name := "resy-booking-bot"
organization := "com.resy"

scalaVersion := "2.13.12"

ThisBuild / scalafixDependencies ++= Seq(
  "com.github.vovapolu" %% "scaluzzi"         % "0.1.23",
  "org.scalatest"       %% "autofix"          % "3.1.0.1",
  "com.eed3si9n.fix"    %% "scalafix-noinfer" % "0.1.0-M1"
)

val enableGithubPackages = sys.env.contains("GITHUB_TOKEN")

ThisBuild / dynverSeparator := "-"
ThisBuild / dynverSonatypeSnapshots := true
ThisBuild / githubOwner := sys.env.getOrElse("GITHUB_REPOSITORY_OWNER", "n/a")
ThisBuild / githubRepository := name.value
(ThisBuild / githubTokenSource).withRank(KeyRanks.Invisible) :=
  TokenSource.Environment("GITHUB_TOKEN")

lazy val root = Project("resy-booking-bot", file("."))
  .disablePlugins(
    (if (enableGithubPackages) Seq.empty[AutoPlugin] else Seq(GitHubPackagesPlugin)) *
  )
  .settings(
    scalafixOnCompile := true,
    scalafmtOnCompile := true,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions += "-Ywarn-unused",
    libraryDependencies ++= Seq(
      "org.playframework"          %% "play-ahc-ws"     % "3.0.0",
      "com.github.pureconfig"      %% "pureconfig"      % "0.17.4",
      "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.5",
      "ch.qos.logback"              % "logback-classic" % "1.4.14",
      "org.apache.commons"          % "commons-lang3"   % "3.12.0",
      "org.scalatest"              %% "scalatest"       % "3.2.15" % Test,
      "org.mockito"                 % "mockito-core"    % "5.1.1"  % Test
    )
  )

assembly / assemblyMergeStrategy := {
  case PathList("module-info.class")                              => MergeStrategy.discard
  case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.discard
  case x => (assembly / assemblyMergeStrategy).value(x)
}

assembly / artifact := {
  val art = (assembly / artifact).value
  art.withClassifier(Some("assembly"))
}

addArtifact(assembly / artifact, assembly)
