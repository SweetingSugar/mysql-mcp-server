# Externalize Fixed SQL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the two fixed metadata queries out of `DatabaseMetadataService` and load them from classpath SQL resources at application startup without changing query behavior.

**Architecture:** Store one query per file under `src/main/resources/sql`. Spring injects those resources into `DatabaseMetadataService`; the constructor reads each file as UTF-8 and caches the resulting strings, so an unreadable or missing resource prevents bean creation. The service continues to own JDBC execution, parameter binding, and result mapping.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Core `Resource`, Spring JDBC `JdbcTemplate`, JUnit 5, Mockito, Maven.

---

## File Map

- Create `src/main/resources/sql/metadata-list-tables.sql` — fixed query for listing tables and views.
- Create `src/main/resources/sql/metadata-describe-table.sql` — fixed parameterized query for describing one table or view.
- Modify `src/main/java/com/example/mysqlmcpserver/query/DatabaseMetadataService.java:15-45` — replace Java text-block constants and one-argument constructor with resource injection and startup-time loading; leave JDBC callbacks and `?` binding unchanged.
- Create `src/test/java/com/example/mysqlmcpserver/query/DatabaseMetadataServiceResourceTest.java` — verify classpath SQL resources are loaded into query execution and missing resources fail immediately.
- Do not modify `src/test/java/com/example/mysqlmcpserver/query/DatabaseMetadataServiceTest.java` or `src/test/java/com/example/mysqlmcpserver/MysqlMcpServerApplicationTests.java`; the existing integration and application-context tests remain regression checks.

## Task 1: Add failing resource-loading tests

**Files:**
- Create: `src/test/java/com/example/mysqlmcpserver/query/DatabaseMetadataServiceResourceTest.java`

- [ ] **Step 1: Write the tests against the intended constructor contract.**

```java
package com.example.mysqlmcpserver.query;

import com.example.mysqlmcpserver.query.model.TableSummary;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseMetadataServiceResourceTest {

    @Test
    void loadsListTablesSqlFromClasspathResource() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<TableSummary>>any()))
                .thenReturn(List.of());

        DatabaseMetadataService service = new DatabaseMetadataService(
                jdbcTemplate,
                new ClassPathResource("sql/metadata-list-tables.sql"),
                new ClassPathResource("sql/metadata-describe-table.sql"));

        service.listTables();

        verify(jdbcTemplate).query(
                org.mockito.ArgumentMatchers.<String>argThat(sql -> sql.contains("FROM information_schema.tables")
                        && sql.contains("ORDER BY TABLE_NAME")),
                org.mockito.ArgumentMatchers.<RowMapper<TableSummary>>any());
    }

    @Test
    void failsDuringConstructionWhenSqlResourceCannotBeRead() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        assertThatThrownBy(() -> new DatabaseMetadataService(
                jdbcTemplate,
                new ClassPathResource("sql/missing-query.sql"),
                new ClassPathResource("sql/metadata-describe-table.sql")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to load SQL resource");
    }
}
```

- [ ] **Step 2: Run the new test before changing production code.**

Run:

```bash
mvn -Dtest=DatabaseMetadataServiceResourceTest test
```

Expected: compilation fails because `DatabaseMetadataService` still has only its existing `JdbcTemplate` constructor. This confirms the test exercises the new contract rather than passing against the old implementation.

## Task 2: Add the classpath SQL resources

**Files:**
- Create: `src/main/resources/sql/metadata-list-tables.sql`
- Create: `src/main/resources/sql/metadata-describe-table.sql`

- [ ] **Step 1: Add the table-list query without Java indentation or a trailing semicolon.**

```sql
SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE
FROM information_schema.tables
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME
```

- [ ] **Step 2: Add the parameterized table-description query.**

```sql
SELECT c.TABLE_SCHEMA, c.TABLE_NAME, t.TABLE_TYPE,
       c.COLUMN_NAME, c.DATA_TYPE, c.COLUMN_TYPE, c.IS_NULLABLE,
       c.COLUMN_DEFAULT, c.CHARACTER_SET_NAME, c.COLLATION_NAME,
       k.ORDINAL_POSITION AS PRIMARY_KEY_POSITION
FROM information_schema.columns c
JOIN information_schema.tables t
  ON t.TABLE_SCHEMA = c.TABLE_SCHEMA
 AND t.TABLE_NAME = c.TABLE_NAME
LEFT JOIN information_schema.key_column_usage k
  ON k.TABLE_SCHEMA = c.TABLE_SCHEMA
 AND k.TABLE_NAME = c.TABLE_NAME
 AND k.COLUMN_NAME = c.COLUMN_NAME
 AND k.CONSTRAINT_NAME = 'PRIMARY'
WHERE c.TABLE_SCHEMA = DATABASE()
  AND c.TABLE_NAME = ?
ORDER BY c.ORDINAL_POSITION
```

- [ ] **Step 3: Check the resource files for whitespace errors.**

Run:

```bash
git diff --check
```

Expected: no output.

## Task 3: Load resources during service construction

**Files:**
- Modify: `src/main/java/com/example/mysqlmcpserver/query/DatabaseMetadataService.java:1-45,47,62,91`

- [ ] **Step 1: Replace the SQL constants and add the required imports.**

Add these imports:

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
```

Replace both text-block constants with cached instance fields:

```java
private final JdbcTemplate jdbcTemplate;
private final String listTablesSql;
private final String describeTableSql;
```

- [ ] **Step 2: Replace the one-argument constructor with resource-injecting construction.**

```java
public DatabaseMetadataService(
        JdbcTemplate jdbcTemplate,
        @Value("classpath:sql/metadata-list-tables.sql") Resource listTablesResource,
        @Value("classpath:sql/metadata-describe-table.sql") Resource describeTableResource) {
    this.jdbcTemplate = jdbcTemplate;
    this.listTablesSql = readSql(listTablesResource);
    this.describeTableSql = readSql(describeTableResource);
}
```

- [ ] **Step 3: Add the startup-time resource reader.**

Add this private method before `primaryKeyPosition`:

```java
private static String readSql(Resource resource) {
    try (var inputStream = resource.getInputStream()) {
        return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
    }
    catch (IOException exception) {
        throw new IllegalStateException(
                "Unable to load SQL resource: " + resource.getDescription(),
                exception);
    }
}
```

This method deliberately throws an unchecked exception so Spring cannot create the service bean when a packaged SQL resource is missing or unreadable.

- [ ] **Step 4: Use the cached SQL fields without changing JDBC behavior.**

Change only the two `JdbcTemplate` calls:

```java
List<TableSummary> tables = jdbcTemplate.query(
        listTablesSql,
        (resultSet, rowNum) -> new TableSummary(
                resultSet.getString("TABLE_SCHEMA"),
                resultSet.getString("TABLE_NAME"),
                resultSet.getString("TABLE_TYPE")));
```

```java
List<TableRow> rows = jdbcTemplate.query(
        describeTableSql,
        preparedStatement -> preparedStatement.setString(1, tableName),
        (resultSet, rowNum) -> new TableRow(
                resultSet.getString("TABLE_SCHEMA"),
                resultSet.getString("TABLE_NAME"),
                resultSet.getString("TABLE_TYPE"),
                new ColumnDescription(
                        resultSet.getString("COLUMN_NAME"),
                        resultSet.getString("DATA_TYPE"),
                        resultSet.getString("COLUMN_TYPE"),
                        resultSet.getString("IS_NULLABLE"),
                        resultSet.getObject("COLUMN_DEFAULT"),
                        resultSet.getString("CHARACTER_SET_NAME"),
                        resultSet.getString("COLLATION_NAME"),
                        primaryKeyPosition(resultSet))));
```

Do not alter the `preparedStatement.setString(1, tableName)` binding or any result mapping.

## Task 4: Run focused and packaging verification

**Files:**
- No additional files.

- [ ] **Step 1: Run the focused unit test.**

Run:

```bash
mvn -Dtest=DatabaseMetadataServiceResourceTest test
```

Expected: both tests pass. The first proves the loaded list query reaches `JdbcTemplate`; the second proves a missing resource fails during construction.

- [ ] **Step 2: Run the existing application-context test.**

Run:

```bash
mvn -Dtest=MysqlMcpServerApplicationTests test
```

Expected: the Spring context can resolve both `classpath:` resources and the application wiring remains valid. In the current checkout, this test references `src/test/resources/db/init.sql`, but that file is not present in the working tree; if the test fails before application startup for that pre-existing reason, report it separately rather than changing the SQL migration scope.

- [ ] **Step 3: Verify resources are packaged into the executable JAR.**

Run:

```bash
mvn -DskipTests package
jar tf target/mysql-mcp-server-0.0.1-SNAPSHOT.jar | grep 'BOOT-INF/classes/sql/metadata-.*-tables.sql\|BOOT-INF/classes/sql/metadata-describe-table.sql'
```

Expected: both SQL resource paths appear under `BOOT-INF/classes/sql/`.

- [ ] **Step 4: Review the final diff for security and scope.**

Run:

```bash
git diff --check
git status --short
git diff -- src/main/java/com/example/mysqlmcpserver/query/DatabaseMetadataService.java src/main/resources/sql src/test/java/com/example/mysqlmcpserver/query/DatabaseMetadataServiceResourceTest.java
```

Confirm that no user-controlled value is concatenated into SQL, the `?` placeholder remains, and no unrelated files are modified. Do not create a Git commit unless the user explicitly requests one.
