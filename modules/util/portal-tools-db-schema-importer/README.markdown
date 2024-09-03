# Database Schema Importer Tool

This tool imports database schemas between different databases in the same
network.

## Requirements

- MySQL or PostgreSQL
- Database user with read access to all partitions
- Previous execution of DBSchemaDefinitionExporter to generate sql schema files.

## Usage

```
./db_schema_importer.sh <parameters>
```

Import parameters:

- `--source-jdbc-fetch-size <arg>` Set the source DB fetch size. Default, 2500.
- `--source-jdbc-url <arg>` Set the source JDBC URL.
- `--source-user <arg>` Set the source database user.
- `--source-password <arg>` Set the source database user password.
- `--target-jdbc-batch-size <arg>` Set the target DB batch size. Defaul, 2500.
- `--target-jdbc-url <arg>` Set the target JDBC URL.
- `--target-user <arg>` Set the target database user.
- `--target-password <arg>` Set the target database user password.
- `--path <arg>` Set the input directory with tables.sql, *indexes.sql files.

## Examples

```
./db_schema_importer.sh --source-jdbc-url "jdbc:mysql://localhost:3306/schema" --source-user "xyz123" --source-password "xyz123" --target-jdbc-url "jdbc:postgresql://localhost:5432/schema" --target-user "xyz321" --target-password "xyz321" --path "/directory/"
./db_schema_importer.sh --source-jdbc-url "jdbc:mysql://localhost:3306/schema" --source-user "xyz123" --source-password "xyz123" --target-jdbc-url "jdbc:postgresql://localhost:5432/schema" --target-user "xyz321" --target-password "xyz321" --path "/directory/" --source-jdbc-fetch-size 2500 --target-jdbc-batch-size 2500
```