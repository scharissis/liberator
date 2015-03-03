package com.liberator

import org.json4s._
import org.json4s.jackson.JsonMethods._
import sun.misc.BASE64Decoder

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.rdd.RDD

import com.liberator.PackageJson._

// Converts NodeJS 'package.json' files into Liberator PackageJson's (aka. RepDep files).
// TODO: Merge outputs by package?
object Reformer {

  implicit lazy val formats = org.json4s.DefaultFormats

  def decodeBase64(data: String) : String = {
    val bytes = new sun.misc.BASE64Decoder().decodeBuffer(data)
    return bytes.map(_.toChar).mkString
  }

  // cur/prev: (timestamp, commit, data)
  def node2package( prev: (String,String,String), cur: (String,String,String), index: Int) : List[PackageJson] = {
    val timestamp = cur._1
    val commit = cur._2
    val fromFile = prev._3
    val toFile = cur._3

    val parsedFromFile : org.json4s.JValue = parse(fromFile)
    val parsedToFile : org.json4s.JValue = parse(toFile)

    val source = (parsedFromFile \ "source") match {
      case JNothing => "unknown"
      case default => default.extract[String]
    }
    var url = "failed-to-parse-url"
    scala.util.control.Exception.ignoring(classOf[Exception]) { // Ignore ALL Exceptions!
      url = (parsedToFile \ "url").extract[String]
    }
    val decodedContentFrom : String = decodeBase64(
      ( parsedFromFile \ "content" ) match {
        case JNothing => "Failed to decode content."
        case default => default.extract[String]
      }
    )
    val decodedContentTo : String = decodeBase64(
      ( parsedToFile \ "content" ) match {
        case JNothing => "Failed to decode content."
        case default => default.extract[String]
      }
    )

    var parsedPackageFrom: org.json4s.JValue = org.json4s.JNothing
    try{
      parsedPackageFrom = parse(decodedContentFrom)
    } catch { // Bad JSON
      case e: Exception => println("Warning: Failed to parse content of decodedContentFrom " + url + " (commit: "+commit+"):\n" + e
        + "\n --- File Contents (Decoded) ---\n" + decodedContentFrom + "\n ------------------")
      return List(PackageJson("ERROR","", List[Dependency]()))
    }
    var parsedPackageTo: org.json4s.JValue = org.json4s.JNothing
    try{
      parsedPackageTo = parse(decodedContentTo)
    } catch { // Bad JSON
      case e: Exception => println("Warning: Failed to parse content of decodedContentTo " + url + " (commit: "+commit+"):\n" + e
        + "\n --- File Contents (Decoded) ---\n" + decodedContentTo + "\n ------------------")
      return List(PackageJson("ERROR","", List[Dependency]()))
    }

    // Dependencies are of the form: 'gruntjs': '0.4.2'
    val devDepsFrom : Map[String,String] = ( parsedPackageFrom \ "devDependencies" ) match {
      case JNothing => Map()
      case JArray(List()) => Map()  // TODO: Properly handle this naughty file.
      case default => default.extract[Map[String,String]]
    }
    val depsFrom : Map[String,String] = ( parsedPackageFrom \ "dependencies" ) match {
      case JNothing => Map()
      case JArray(List()) => Map()  // TODO: Properly handle this naughty file.
      case default => default.extract[Map[String,String]]
    }
    val devDepsTo : Map[String,String] = ( parsedPackageTo \ "devDependencies" ) match {
      case JNothing => Map()
      case JArray(List()) => Map()  // TODO: Properly handle this naughty file.
      case default => default.extract[Map[String,String]]
    }
    val depsTo : Map[String,String] = ( parsedPackageTo \ "dependencies" ) match {
      case JNothing => Map()
      case JArray(List()) => Map()  // TODO: Properly handle this naughty file.
      case default => default.extract[Map[String,String]]
    }

    val allDepsFrom : Map[String,String] = (devDepsFrom ++ depsFrom)
    val allDepsTo : Map[String,String] = (devDepsTo ++ depsTo)
    val depFromkeys : Set[String] = allDepsFrom.keySet
    val depTokeys : Set[String] = allDepsTo.keySet

    val newDeps : List[Dependency] = depTokeys.diff(depFromkeys)
      .map( dep => (dep, allDepsTo.get(dep)) )  // name => (name,version)
      .map{ p => (p._1, p._2.getOrElse("")) } // (String,Option[String]) => (String,String)
      .map{
        case (name,version) =>   // (dep) = (name,version) => Dependency()
          Dependency(name, List(Event(version, "new", timestamp, commit )))
      }.toList

    val removedDeps : List[Dependency] = depFromkeys.diff(depTokeys)
      .map( dep => (dep, allDepsFrom.get(dep)) )  // name => (name,version)
      .map{ p => (p._1, p._2.getOrElse("")) } // (String,Option[String]) => (String,String)
      .map{
        case (name,version) =>   // (dep) = (name,version) => Dependency()
          Dependency(name, List(Event(version, "removed", timestamp, commit )))
      }.toList

    var updatedDeps : List[Dependency] = List[Dependency]()
    allDepsFrom.foreach {
      case (name,version) =>
        if ( allDepsTo.contains(name) && allDepsFrom.get(name) != allDepsTo.get(name) ) {
          updatedDeps = Dependency(name, List(Event(version, "updated", timestamp, commit ))) :: updatedDeps
        }
    }

    val name = (parsedPackageFrom \ "name") match {
      case JNothing => "unknown"
      case default => default.extract[String]
    }

    val packages: List[PackageJson] = List( PackageJson(name, source, newDeps++removedDeps++updatedDeps) )

    // The first pair is faked into here to produce a list of 'new' deps.
    if (index == 0) {
      val prevTimestamp = prev._1
      val prevCommit = prev._2
      val firstPackage = List(PackageJson(name,source,
        allDepsFrom.map{
          case (name,version) =>   // (dep) = (name,version) => Dependency()
            Dependency(name, List(Event(version, "new", prevTimestamp, prevCommit )))
        }.toList
      ))
      return firstPackage
    }
    return packages
  }

  // Returns: (repoName, (timestamp, commit, data))
  // Input: (.../facebook/react/package_1391185509000_bff9731b66093239dc0408fb1d83df423925b6f9.json, <data>)
  // Output: (jade, (1415567992, 2314090c37c2a3ff5c5ae62c77cb6680201475fa, <data>))
  // TODO: The key (first String) should be more unique (hash of company+repo?), as we groupBy it.
  def splitPath(pair: (String, String)): (String, (String, String, String)) = {
    val pathList = pair._1.split('/')
    val repoName = pathList(pathList.size-2)
    val fileList = pathList.last.split('_')
    val timestamp = (fileList(1).toLong/1000).toString
    val commit = fileList(2).split('.').head
    val data = pair._2
    return (repoName, (timestamp, commit, data))
  }

  def run(sc : SparkContext,
    source:String = "../crawler/output/repos/raw/github",
    file_regex:String = "/*/*/package_*.json",
    output_dir:String = "") : org.apache.spark.rdd.RDD[Iterable[PackageJson]] = {

    // Read & Parse JSON files into NodeJS Packages.
    val nodefiles: org.apache.spark.rdd.RDD[(String,Iterable[(String,String,String)])] =
      sc.wholeTextFiles(source + file_regex)
      .map(p => splitPath(p))
      .groupByKey()

    val f : (String,String,String) = nodefiles.first._2.head
    val first : Iterable[((String,String,String),(String,String,String))] = Iterable((f,f))
    val packages: org.apache.spark.rdd.RDD[(String,Iterable[PackageJson])] = {
      nodefiles.map{ case (repoName, repoTripletPair) =>  // (repoName, (timestamp, commit, data))
        (repoName, ( first ++ repoTripletPair.zip(repoTripletPair.tail)).zipWithIndex.flatMap{
          case ((prev,cur), index) => node2package(prev, cur, index)
        })
      }
    }

    val output = packages
      .map{ case (pname, iterable) => iterable.map{
        case (pack) => pack
      }}

    if (output_dir != ""){
      output
      .map( m => pretty(render(Extraction.decompose(m))) )  // To JSON
      .saveAsTextFile(output_dir)
    }

    println("Reformed " + packages.count.toString + " packages.")

    return output
  }

  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Liberator Reformer").setMaster("local[2]")
    val sc = new SparkContext(conf)

    val _ = run(sc, output_dir = "output")
  }
}
