DROP TABLE liberator_nodejs;

CREATE TABLE liberator_nodejs (
	package_id text,
	usage_date DATE,
	usage_count INT,
	CONSTRAINT liberator_nodejs_PK PRIMARY KEY (package_id, usage_date)
);
