package com.liberator

import org.apache.spark._
import org.apache.spark.rdd.RDD

// Define a Package format.
case class Event(version: String, event: String, time: String, commit: String)
case class Dependency(name: String, usage: List[Event])
case class PackageJson(name: String, source: String, dependencies: List[Dependency])

object PackageJsonUtil {

  def addPackageJsonDependencies (a:Dependency, b:Dependency): Dependency = {
    Dependency(a.name, (a.usage++b.usage).sortBy(_.commit))
  }

  // Joins two PackageJson's, concatenating their dependencies.
  // Note: Assumes the name and source are the same.
  def addPackageJson (a:PackageJson, b:PackageJson): PackageJson = {
    PackageJson (a.name, a.source,
      (a.dependencies++b.dependencies)
      .groupBy(_.name)
      .mapValues(_.reduce(addPackageJsonDependencies))
      .values
      .toList
      .sortBy(_.name)
    )
  }

}
