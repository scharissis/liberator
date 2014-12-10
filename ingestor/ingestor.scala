import org.json4s._
import org.json4s.jackson.JsonMethods._

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD

// Input: Files containing Lists of RepDep files.
// Output: Files containing number of dependencies per package.
// TODO: Add timestamp to graph edges.
object Ingestor {
  // Transform strings for consistent hashing.
  def sanitiseString(s: String): String = {
    s.toLowerCase.replace(" ", "").replace("-", "")
  }

  // Generate a pseudo-Unique VertexId of type Long.
  def hash(s: String): Long = { sanitiseString(s).hashCode.toLong }

  // Define a Package format.
  implicit val formats = org.json4s.DefaultFormats
  case class Event(version: String, event: String, time: String, commit: String)
  case class Dependency(name: String, usage: List[Event])
  case class Package(name: String, source: String, dependencies: List[Dependency])

  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Liberator Ingestor")
    val sc = new SparkContext(conf)
    val input_files = "../reformer/output/part-*"
    val output_file = "output"

    // Read & Parse JSON files into Packages.
    // TODO: Ignore empty files needed?
    val packages: org.apache.spark.rdd.RDD[Package] = sc.wholeTextFiles(input_files)
      .filter{ case (filename,filecontent) => filecontent != "" } // Skip empty files.
      .map(json  =>  { parse(json._2) })                          // Discard filename.
      .map(json  =>  json.extract[ List[Package] ])
      .flatMap(identity)
      .cache

    // Generate Vertex RDD.
    val vertices: VertexRDD[String] = VertexRDD(
      packages.flatMap(p => { p.dependencies.map( d => { (hash(d.name), d.name)}) })
        .union(packages.map(p => { (hash(p.name), p.name) }))
        .distinct
    )

    // Generate Edge RDD.
    // Define: connection(a,b) => 'a is a dependency of b'.
    val edges: org.apache.spark.rdd.RDD[Edge[String]] = packages
      .flatMap(p => { p.dependencies.map( d => {Edge(hash(p.name), hash(d.name), "dep")}) })

    // Build the initial Graph.
    val graph: Graph[String, String] = Graph(vertices, edges)

    // Obtain dependency subgraph.
    val subgraph = graph.subgraph(
      //vpred = (verexId,vd) => vd == "grunt",
      epred = e => e.attr == "dep"
    ).cache()

    // Get the inDegree RDD.
    val gDegrees = subgraph.inDegrees

    // Translate vertexId's back to String's.
    val result = subgraph.vertices.innerJoin(gDegrees){
      (vid, vd, o) => (vd, o)
    }.map( x => x._2 ).filter{ x => x._1 != "null"}

    // Write result to file.
    result.saveAsTextFile(output_file)
  }
}
