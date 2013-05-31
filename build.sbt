// Omni jar 
import AssemblyKeys._ 

assemblySettings

// One JAR
seq(com.github.retronym.SbtOneJar.oneJarSettings: _*) 

libraryDependencies += "commons-lang" % "commons-lang" % "2.6"

libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor" % "2.2-M3",
"com.typesafe.akka" %% "akka-remote" % "2.2-M3",
"com.typesafe.akka" %% "akka-cluster-experimental" % "2.2-M3",
"org.apache.thrift" % "libthrift" % "0.9.0"
)

// Actual project details

name := "AkkaThrift"

version := "0.1"

scalaVersion := "2.10.1"

