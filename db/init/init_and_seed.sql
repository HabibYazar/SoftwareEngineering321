-- 1. Create tables matching your CSV structure
CREATE TABLE IF NOT EXISTS departments (
    row_num INT,
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    group_name VARCHAR(150),
    clinician_count INT,
    source_url TEXT
);

CREATE TABLE IF NOT EXISTS clinicians (
    row_num INT,
    id UUID PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL,
    department VARCHAR(150),
    department_id UUID REFERENCES departments(id),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    title VARCHAR(100)
);

-- 2. Import the CSV files directly (departments first due to foreign key!)
COPY departments(row_num, id, name, group_name, clinician_count, source_url)
FROM '/docker-entrypoint-initdb.d/departments.csv'
DELIMITER ','
CSV HEADER;

COPY clinicians(row_num, id, display_name, department, department_id, first_name, last_name, title)
FROM '/docker-entrypoint-initdb.d/clinicians.csv'
DELIMITER ','
CSV HEADER;
