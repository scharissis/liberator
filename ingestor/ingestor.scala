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
    val input_files = "test/*.json"
    val output_file = "packages.out"

    // Read & Parse JSON files.
    val json_rdd = sc.wholeTextFiles(input_files)
    val pkg: org.apache.spark.rdd.RDD[String] = json_rdd.map(
      json => { parse(json._2) }).map( json => { json.extract[Package] }).map(p => {p.name + "," + p.dependencies.size})

    // Write result to file.
    pkg.saveAsTextFile(output_file)
  }
}
