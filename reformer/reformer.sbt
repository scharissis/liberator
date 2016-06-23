name := "Reformer"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.apache.spark"  %% "spark-core"     % "1.6.1"   % "provided",
  "org.json4s"        %% "json4s-native"  % "3.2.10",
  "org.json4s"        %% "json4s-jackson" % "3.2.10",
  "org.scalatest"      % "scalatest_2.11" % "2.2.6" % "test"
)
