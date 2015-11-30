name := "acked-stream-demo"

scalaVersion := "2.11.7"

//resolvers += "Twitter repo" at "http://maven.twttr.com/"

libraryDependencies ++= Seq(

//  "com.typesafe.akka" % "akka-stream-experimental_2.11" % "2.0-M1" withSources() withJavadoc(),
  "com.typesafe.akka" %% "akka-stream-experimental" % "1.0" withSources() withJavadoc(),
  "com.timcharper" %% "acked-streams" % "1.0-RC1"  withSources() withJavadoc()
  //"com.twitter" % "util-eval" % "1.12.13"
  //"com.twitter" % "util-eval_2.10" % "6.1.0"
  //"com.twitter" %% "util-collection" % "6.27.0"   withSources() withJavadoc()
  //"com.twitter" % "util-eval" % "6.5.0"   withSources()
)
