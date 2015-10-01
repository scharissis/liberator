# Due to a bug in SPARK, I've included the postgres JAR 3 ways. Only '--jars' should be required.
# Possibly related: https://github.com/apache/spark/pull/5782
default: db build
	@echo "[make] RUNNING..."
	SPARK_CONF_DIR="${PWD}/conf" \
	SPARK_CLASSPATH=`find ~/.ivy2 -iname "*postgresql-9*.jar" | head -1` spark-submit \
		--class "com.liberator.Ingestor" \
		target/scala-2.10/Ingestor-assembly-1.0.jar \
		--jars `find ~/.ivy2 -iname "*postgresql-9*.jar" | head -1` \
		--driver-class-path `find ~/.ivy2 -iname "*postgresql-9*.jar" | head -1`
	@echo ""

build: clean
	@echo "[make] BUILDING..."
	sbt assembly
	@echo ""

db:
	PGPASSWORD=liberator psql -h 127.0.0.1 -a -f db/create_tables.sql liberator liberator

db_backup:
	PGPASSWORD=liberator pg_dump -h localhost -U liberator liberator > db/dumps/liberator_`date +%Y%m%d`.dump

report: default
	@echo "[make] REPORTING..."
	PGPASSWORD=liberator psql -h 127.0.0.1 -a -f db/get_data.sql liberator liberator
	@echo ""

debug: report
	@echo "[make] DEBUGGING..."
	./test/debug/neo4j.sh
	@echo ""

cluster:
	./spark-ec2 --key-pair=x1c --identity-file=~/.ssh/x1c.pem --region=ap-southeast-2 --zone=ap-southeast-2a -s 3 launch liberator-cluster

test: clean
	@echo "[make] TESTING..."
	sbt test

clean:
	@echo "[make] CLEANING..."
	rm -rf output
	rm -rf test/debug/output
	@echo ""

.PHONY: clean db db_backup debug cluster test
