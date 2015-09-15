default: db build
	@echo "[make] RUNNING..."
	spark-submit \
		--class "com.liberator.Ingestor" \
		target/scala-2.10/Ingestor-assembly-1.0.jar \
		--jar lib/postgresql-9.3-1102-jdbc41.jar
	@echo ""

historical: db build
	@echo "[make] RUNNING HISTORICAL..."
	spark-submit \
		--class "com.liberator.IngestorHistorical" \
		target/scala-2.10/Ingestor-assembly-1.0.jar \
		--jar lib/postgresql-9.3-1102-jdbc41.jar
	@echo ""

build: clean
	@echo "[make] BUILDING..."
	sbt assembly
	@echo ""

db:
	PGPASSWORD=liberator psql -h 127.0.0.1 -a -f db/create_tables.sql liberator_test liberator

db_backup:
	PGPASSWORD=liberator pg_dump -h localhost -U liberator liberator_test > db/dumps/liberator_test_`date +%Y%m%d`.dump

report: default
	@echo "[make] REPORTING..."
	PGPASSWORD=liberator psql -h 127.0.0.1 -a -f db/get_data.sql liberator_test liberator
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
