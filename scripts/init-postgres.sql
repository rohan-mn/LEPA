-- Run as a PostgreSQL superuser if you want the optional PostgreSQL profile.
-- Change the password if desired, and keep .env consistent.
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'lepa') THEN
    CREATE ROLE lepa LOGIN PASSWORD 'lepa123';
  END IF;
END
$$;

SELECT 'CREATE DATABASE lepa OWNER lepa'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'lepa')\gexec
GRANT ALL PRIVILEGES ON DATABASE lepa TO lepa;
