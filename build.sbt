// Omni jar 
import AssemblyKeys._ 

assemblySettings

// One JAR
seq(com.github.retronym.SbtOneJar.oneJarSettings: _*) 

libraryDependencies += "commons-lang" % "commons-lang" % "2.6"

libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor" % "2.2.0-RC1",
"com.typesafe.akka" %% "akka-remote" % "2.2.0-RC1",
"com.typesafe.akka" %% "akka-cluster" % "2.2.0-RC1",
"org.apache.thrift" % "libthrift" % "0.9.0",
"ch.qos.logback" % "logback-classic" % "1.0.6"
)

fork in run := true

// Actual project details

name := "AkkaThrift"

version := "0.1"

scalaVersion := "2.10.1"

