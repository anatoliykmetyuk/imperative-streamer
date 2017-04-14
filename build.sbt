// enablePlugins(ScalaJSPlugin)

val ScalaVer = "2.12.1"

val Cats          = "0.9.0"
val Shapeless     = "2.3.2"
val KindProjector = "0.9.3"
val Circe         = "0.7.0"

val ScalaJSDom = "0.9.1"
val ScalaTags  = "0.6.3"

val ApacheIO = "2.5"
val ApacheCodec = "1.10"

val outPath = new File("assets")
val jsPath = outPath / "js"

scalaVersion in ThisBuild := ScalaVer

lazy val commonSettings = Seq(
  name    := "imperativestreamer"
, version := "0.1.0"
, scalaVersion := ScalaVer
, libraryDependencies ++= Seq(
    "org.typelevel" %%% "cats"      % Cats
  , "com.chuusai"   %%% "shapeless" % Shapeless

  , "io.circe"      %%% "circe-core"    % Circe
  , "io.circe"      %%% "circe-generic" % Circe
  , "io.circe"      %%% "circe-parser"  % Circe

  , "com.lihaoyi" %%% "scalatags" % ScalaTags
  )
, addCompilerPlugin("org.spire-math" %% "kind-projector" % KindProjector)
, scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:experimental.macros",
      "-unchecked",
      // "-Xfatal-warnings",
      "-Xlint",
      // "-Yinline-warnings",
      "-Ywarn-dead-code",
      "-Xfuture",
      "-Ypartial-unification")
)

lazy val root = project.in(file(".")).
  aggregate(js, jvm)
  .settings(
    publish := {},
    publishLocal := {}
  )

lazy val imperativestreamer = crossProject.in(file("."))
  .settings(commonSettings)

lazy val jvm = imperativestreamer.jvm
  .settings(
    libraryDependencies ++= Seq(
      "commons-io" % "commons-io" % ApacheIO
    , "commons-codec" % "commons-codec" % ApacheCodec
    )
  , baseDirectory in reStart := new File(".")
  , reStart <<= reStart.dependsOn(fastOptJS in (js, Compile))
  )

lazy val js  = imperativestreamer.js
  .settings(
    scalaJSUseMainModuleInitializer := true
  , libraryDependencies ++= Seq(
      "org.scala-js"  %%% "scalajs-dom" % ScalaJSDom
    )
  , artifactPath in (Compile, fastOptJS) := jsPath / "application.js"
  )
