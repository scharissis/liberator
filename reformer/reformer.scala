import org.json4s._
import org.json4s.jackson.JsonMethods._

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.rdd.RDD

// Converts NodeJS 'package.json' files into Liberator Packages.
// TODO: Group by library (merge all revisions of each lib).
// TODO: Determine real Events.
// TODO: Determine source (eg. 'Github').
object Reformer {
  // Define a Package format.
  implicit lazy val formats = org.json4s.DefaultFormats
  case class Event(version: String, event: String, time: String, commit: String)
  case class Dependency(name: String, usage: List[Event])
  case class Package(name: String, source: Option[String], dependencies: List[Dependency])

  def node2package(nodeFile: (String,String)) : Package = {
    val parsed_file : org.json4s.JValue = parse(nodeFile._2)

    val name = (parsed_file \ "name").extract[String]
    val devdeps : Map[String,String] = ( parsed_file \ "devDependencies" ) match {
      case JNothing => Map()
      case default => default.extract[Map[String,String]]
    }
    val deps : Map[String,String] = ( parsed_file \ "dependencies" ) match {
      case JNothing => Map()
      case default => default.extract[Map[String,String]]
    }
    val dependencies : List[Dependency] = (devdeps ++ deps).map(
        (dep) => {  // (dep) = (name,version)
        Dependency(dep._1, List(Event(dep._2, "add", "1415152149489", "fab1e2afbbff3c7c454946bbddc2648d8a673d04" )))
      }
    ).toList
    return Package(name, Option("github"), dependencies)
  }

  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Liberator Reformer")
    val sc = new SparkContext(conf)
    val input_files = "test/*.json"
    val output_file = "output"

    // Read & Parse JSON files into NodeJS Packages.
    val json_packages: org.apache.spark.rdd.RDD[String] = sc.wholeTextFiles(input_files)
      .map(node2package)
      .map( m => pretty(render(Extraction.decompose(m))) )

    // Write result to file.
    json_packages.saveAsTextFile(output_file)
  }
}
