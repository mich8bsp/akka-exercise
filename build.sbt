ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.7"

Compile / PB.targets := Seq(
  scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
)

lazy val root = (project in file("."))
  .settings(
    name := "akka-exercise"
  )

val AkkaVersion = "2.6.18"
val AkkaHttpVersion = "10.2.7"
val CatsVersion = "2.7.0"
val ScalaTestVersion = "3.2.10"
val JsoupVersion = "1.14.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
  "org.typelevel" %% "cats-core" % CatsVersion,
  "org.jsoup" % "jsoup" % JsoupVersion,
  "org.scalatest" %% "scalatest" % ScalaTestVersion % "test",
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
)