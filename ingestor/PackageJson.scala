package com.liberator

// Define a Package format.
case class Event(version: String, event: String, time: String, commit: String)
case class Dependency(name: String, usage: List[Event])
case class PackageJson(name: String, source: String, dependencies: List[Dependency])
