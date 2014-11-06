Reformer
=
Normalises NodeJS 'package.json' files into Liberator Packages.
package.json files are described here: https://www.npmjs.org/doc/files/package.json.html

Dependencies
==
* Scala <br/>
  `sudo apt-get install scala`

* Scala SBT <br/>
  http://www.scala-sbt.org/download.html

* Apache Spark <br/>


Build
==
`sbt package`


Run
==
It is presently assumed that all input files are located in folder test: "test/*.json".

To run:

`spark-submit --class "Reformer" --master local[2] target/scala-2.10/reformer_2.10-1.0.jar`

The output will be in output/part-*:
`cat output/part-*`
