default: build
	spark-submit --class "com.liberator.Reformer" target/scala-2.10/reformer_2.10-1.0.jar

build: clean
	sbt package

test: build
	sbt test

clean:
	rm -rf output
