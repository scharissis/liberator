name := "Ingestor"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "org.apache.spark"        %% "spark-core"     % "1.1.0" % "provided",
  "org.apache.spark"        %% "spark-graphx"   % "1.1.0" % "provided",
  "org.json4s"              %% "json4s-native"  % "3.2.10",
  "org.json4s"              %% "json4s-jackson" % "3.2.10",
  "org.scalikejdbc"         %% "scalikejdbc"    % "2.2.0",
  "com.h2database"          % "h2"              % "1.4.182",
  "ch.qos.logback"          % "logback-classic" % "1.1.2",
  "com.github.nscala-time"  %% "nscala-time"    % "1.6.0"
)
