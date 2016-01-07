
import sbt.Keys._

name := "acked-stream-demo"

scalaVersion := "2.11.7"

parallelExecution in Test := true

libraryDependencies ++= Seq(

  "org.scala-lang.modules"      %% "scala-java8-compat"  % "0.3.0" withSources(),
  "com.typesafe.akka" %% "akka-stream-experimental" % "1.0" withSources() withJavadoc(),
  "com.timcharper" %% "acked-streams" % "1.0-RC1"  withSources() withJavadoc(),
  "org.scalatest"               %% "scalatest"           % "2.2.1" % "test"
)

val myPath= SettingKey[File]("mypath")
val val1= SettingKey[File]("val1")
val filter = ScopeFilter( inProjects(ThisProject), inConfigurations(Compile), inTasks(packageBin) )
val myMappings = SettingKey[Seq[(File,String)]]("mymappings")
val task2 = TaskKey[Unit]("task2")

lazy val nameAndProjectID = Def.task {
  (name.value, projectID.value, baseDirectory.value)
}

def makeZip(mappings: Seq[(File,String)],dir: File): File ={
  val zip = dir / "zipdir"
  IO.zip(mappings,zip)
  zip
}

lazy val root = Project("acked-stream-demo", file(".")).settings(

  myPath <<= baseDirectory(_ / "licenses"),

  TaskKey[Unit]("zip") <<= baseDirectory map { bd => IO.zip(Path.allSubpaths(new
      File(bd + "/src/main/scala")),new File(bd + "/src1.zip")) },

//  task2 <<= baseDirectory map { bd =>
//    val zip2=new File(bd + "/src1.zip")
//    //IO.zip(Path.allSubpaths(new File(bd + "/src/main/scala")),zip)
//    println(zip2.getPath)
//    zip2
//  },

  TaskKey[Unit]("time") <<= packagedArtifact.in( ThisProject, Compile, packageBin) map {
    case (art: Artifact, artfile: File) =>
      println("Packaged file: " + artfile.getName)
      println("OK4 !!!")
  },

  TaskKey[Unit]("task1") :=   {
  nameAndProjectID.all(ScopeFilter(inProjects(ThisProject), inConfigurations(Compile), inTasks(packageBin))).value.foreach {
    case (name, id,bd) =>
      println(s"Subproject ${id}, $name,${bd}")
    }
  },

  TaskKey[Unit]("task2") <<= (packagedArtifact.in(Compile,packageBin), baseDirectory) map  {
    case ((art: Artifact, artfile: File),bd:File) =>
      println(artfile.getPath)
      println(bd.getPath)
      println("OK")

    null
  }

)

//inConfig(Compile)(Seq(
//  mappings in Compile := Seq.empty,
//  mappings in Compile <+= (packageBin in Compile) map (_ -> "main.jar"),
//  packageBin <<= (mappings,baseDirectory) map makeZip
//))

