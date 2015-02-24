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
`sbt package` or `make build`


Test
==
`sbt test` or `make test`


Run
==
It is presently assumed that all input files are located in folder test: "test/*.json".

To run:
`make`

The output will be in output/part-* files, if an output directory is specified:
`cat output/part-*`
