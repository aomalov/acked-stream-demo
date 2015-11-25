name := "acked-stream-demo"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(

//  "com.typesafe.akka" % "akka-stream-experimental_2.11" % "2.0-M1" withSources() withJavadoc(),
  "com.typesafe.akka" %% "akka-stream-experimental" % "1.0" withSources() withJavadoc(),
  "com.timcharper" %% "acked-streams" % "1.0-RC1"  withSources() withJavadoc()
)
