import org.json4s._
import org.json4s.jackson.JsonMethods._

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD

object Ingestor {
  implicit val formats = org.json4s.DefaultFormats
  case class Event(version: String, event: String, time: String, commit: String)
  case class Dependency(name: String, usage: List[Event])
  case class Package(name: String, dependencies: List[Dependency])

  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Liberator Ingestor")
    val sc = new SparkContext(conf)
    val input_file = "test/grunt.json"
    val output_file = "packages.out"

    // Read file and parse into a Package.
    val source = scala.io.Source.fromFile(input_file)
    val json_file = source.getLines mkString "\n"
    source.close()
    val json = parse(json_file)
    val pkg: Package = json.extract[Package]

    // Write result to file.
    scala.tools.nsc.io.File(output_file).writeAll(pkg.name + "," + pkg.dependencies.size + "\n")
  }
}
