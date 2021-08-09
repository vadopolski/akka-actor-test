name := "akka-actor-test"

version := "0.1"

scalaVersion := "2.12.7"

val akkaVersion      = "2.6.9"
val scalaTestVersion = "3.2.9"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"               % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit"             % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion,
  "org.scalatest"     %% "scalatest"                % scalaTestVersion,
  "com.typesafe.akka" %% "akka-actor-typed"         % akkaVersion,
  "org.scalatest"     %% "scalatest-wordspec"       % scalaTestVersion
)