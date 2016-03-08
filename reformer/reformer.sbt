name := "Reformer"

version := "1.0"

scalaVersion := "2.10.5"

libraryDependencies ++= Seq(
  "org.apache.spark"  %% "spark-core"     % "1.6.0"   % "provided",
  "org.json4s"        %% "json4s-native"  % "3.2.10",
  "org.json4s"        %% "json4s-jackson" % "3.2.10",
  "org.scalatest"      % "scalatest_2.10" % "2.2.1"   % "test"
)
