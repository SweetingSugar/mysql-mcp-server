# MySQL MCP Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring Boot 空骨架中实现一个带 Bearer Token 认证、通过 Streamable HTTP 提供三个只读 MySQL 查询工具的 MCP Server。

**Architecture:** 使用 Spring AI MCP Server WebMVC starter 托管 MCP 协议和 `/mcp` 端点；使用 `ReadOnlyDatabaseTools` 作为 MCP 工具适配层，将请求交给基于 `JdbcTemplate` 的数据库服务。SQL 在应用层经过单语句只读校验，JDBC 查询同时受只读连接、查询超时、最大行数和最大结果字节数限制，HTTP 端点由独立 Bearer Token 过滤器保护。

**Tech Stack:** Java 17, Spring Boot 3.5.16, Spring AI MCP Server WebMVC 1.1.8, Spring JDBC, MySQL Connector/J, Maven, JUnit 5, Testcontainers MySQL 8.4。

---

## 文件结构

实现完成后，文件职责应保持清晰：

- Modify: `pom.xml` — Spring AI MCP、JDBC、MySQL、Actuator、校验和 Testcontainers 依赖及版本管理。
- Modify: `src/main/resources/application.yml` — MCP Streamable HTTP、数据源和应用限制配置绑定。
- Create: `src/main/java/com/example/mysqlmcpserver/config/McpServerProperties.java` — MCP Token 和端点配置。
- Create: `src/main/java/com/example/mysqlmcpserver/config/QueryProperties.java` — 查询限制配置及启动校验。
- Create: `src/main/java/com/example/mysqlmcpserver/config/McpToolConfiguration.java` — 将工具 Bean 注册为 Spring AI `ToolCallbackProvider`。
- Create: `src/main/java/com/example/mysqlmcpserver/security/BearerTokenFilter.java` — 只保护 `/mcp` 的 Bearer Token 过滤器。
- Create: `src/main/java/com/example/mysqlmcpserver/query/ReadOnlySqlValidator.java` — 单语句、只读 SQL 词法边界校验。
- Create: `src/main/java/com/example/mysqlmcpserver/query/QueryRejectedException.java` — 查询被拒绝的内部异常。
- Create: `src/main/java/com/example/mysqlmcpserver/query/DatabaseResourceNotFoundException.java` — 表或视图不存在的内部异常。
- Create: `src/main/java/com/example/mysqlmcpserver/query/DatabaseQueryException.java` — 数据库查询失败的安全异常。
- Create: `src/main/java/com/example/mysqlmcpserver/query/DatabaseQueryTimeoutException.java` — 数据库查询超时的安全异常。
- Create: `src/main/java/com/example/mysqlmcpserver/query/QueryResult.java` — 查询结果的结构化返回模型。
- Create: `src/main/java/com/example/mysqlmcpserver/query/ReadOnlyQueryService.java` — JDBC 只读查询、超时、行数和结果大小控制。
- Create: `src/main/java/com/example/mysqlmcpserver/query/DatabaseMetadataService.java` — 表、视图、列和主键元数据查询。
- Create: `src/main/java/com/example/mysqlmcpserver/query/model/TableSummary.java` — 表/视图摘要模型。
- Create: `src/main/java/com/example/mysqlmcpserver/query/model/ColumnDescription.java` — 列结构模型。
- Create: `src/main/java/com/example/mysqlmcpserver/query/model/TableDescription.java` — 表结构模型。
- Create: `src/main/java/com/example/mysqlmcpserver/query/model/TableListResult.java` — 表列表工具返回模型。
- Create: `src/main/java/com/example/mysqlmcpserver/query/ReadOnlyDatabaseTools.java` — `@Tool` 方法和安全的工具错误转换。
- Modify: `src/test/java/com/example/mysqlmcpserver/MysqlMcpServerApplicationTests.java` — 使用 Testcontainers 的上下文和 HTTP 集成测试。
- Create: `src/test/java/com/example/mysqlmcpserver/security/BearerTokenFilterTest.java` — 认证头和常量时间校验的单元测试。
- Create: `src/test/java/com/example/mysqlmcpserver/query/ReadOnlySqlValidatorTest.java` — SQL 安全边界的参数化单元测试。
- Create: `src/test/java/com/example/mysqlmcpserver/query/DatabaseMetadataServiceTest.java` — Testcontainers 元数据集成测试。
- Create: `src/test/java/com/example/mysqlmcpserver/query/ReadOnlyQueryServiceTest.java` — 真实 JDBC 查询和结果限制集成测试。
- Create: `src/test/resources/db/init.sql` — Testcontainers 初始化表、视图和测试数据。
- Create: `README.md` — 环境变量、启动方式、MCP 地址和 MySQL 最小权限说明。

---

### Task 1: 添加依赖和基础配置

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/example/mysqlmcpserver/MysqlMcpServerApplicationTests.java`

- [ ] **Step 1: 写出测试依赖和启动配置的失败测试**

把现有上下文测试改成带测试属性的配置绑定测试，先验证所需 Bean 尚未存在：

```java
@SpringBootTest(properties = {
        "app.mcp.auth-token=test-token",
        "app.query.max-rows=1000",
        "app.query.max-result-bytes=1048576",
        "app.query.timeout-seconds=10"
})
class MysqlMcpServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: 运行测试并确认依赖/配置尚未完成**

运行：

```bash
mvn -q -Dtest=MysqlMcpServerApplicationTests test
```

预期：测试因应用配置或 MCP/JDBC 依赖尚未配置而失败；不要通过删除测试来绕过失败。

- [ ] **Step 3: 修改 Maven 依赖和版本管理**

在 `pom.xml` 中增加 Spring AI BOM，并使用与 Spring Boot 3.5.16 兼容的 Spring AI 1.1.8 MCP WebMVC starter；同时加入 JDBC、MySQL、Actuator、Validation 和 Testcontainers：

```xml
<properties>
    <java.version>17</java.version>
    <spring-boot.version>3.5.16</spring-boot.version>
    <spring-ai.version>1.1.8</spring-ai.version>
    <testcontainers.version>1.20.6</testcontainers.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-bom</artifactId>
            <version>${testcontainers.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

依赖列表至少包含：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

不要加入 Spring Security；认证由本计划中的单一 servlet 过滤器实现，避免为静态 Token 引入完整 OAuth 资源服务器。删除现有 Spring Boot Maven Plugin 配置中的 `<skip>true</skip>`，让 `repackage` 在 `mvn verify` 时生成可运行 jar。

- [ ] **Step 4: 写入可覆盖的开发配置**

将 `src/main/resources/application.yml` 更新为：

```yaml
server:
  port: 8090

spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/app?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC}
    username: ${SPRING_DATASOURCE_USERNAME:app}
    password: ${SPRING_DATASOURCE_PASSWORD:}
    hikari:
      read-only: true
  ai:
    mcp:
      server:
        name: mysql-mcp-server
        version: 0.0.1
        type: SYNC
        protocol: STREAMABLE
        instructions: Use the read-only database tools to inspect schema and query data.
        streamable-http:
          mcp-endpoint: /mcp
        capabilities:
          tool: true
  task:
    execution:
      pool:
        core-size: 4

management:
  endpoints:
    web:
      exposure:
        include: health

app:
  mcp:
    endpoint-path: /mcp
    auth-token: ${APP_MCP_AUTH_TOKEN:}
  query:
    max-rows: ${APP_QUERY_MAX_ROWS:1000}
    max-result-bytes: ${APP_QUERY_MAX_RESULT_BYTES:1048576}
    timeout-seconds: ${APP_QUERY_TIMEOUT_SECONDS:10}
```

在配置中使用 Spring AI 1.1.8 的 `spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp` 属性，不手写协议控制器替代自动配置。

- [ ] **Step 5: 运行基础构建**

运行：

```bash
mvn -q -DskipTests compile
```

预期：`BUILD SUCCESS`。此时上下文测试仍可能因尚未创建 `@ConfigurationProperties` Bean 而失败，下一任务补齐这些 Bean。

---

### Task 2: 实现配置属性和 Bearer Token 认证

**Files:**
- Create: `src/main/java/com/example/mysqlmcpserver/config/McpServerProperties.java`
- Create: `src/main/java/com/example/mysqlmcpserver/config/QueryProperties.java`
- Create: `src/main/java/com/example/mysqlmcpserver/security/BearerTokenFilter.java`
- Test: `src/test/java/com/example/mysqlmcpserver/security/BearerTokenFilterTest.java`
- Modify: `src/test/java/com/example/mysqlmcpserver/MysqlMcpServerApplicationTests.java`

- [ ] **Step 1: 写 Bearer Token 过滤器失败测试**

使用 `MockHttpServletRequest`、`MockHttpServletResponse` 和 `FilterChain` 编写以下测试：

```java
class BearerTokenFilterTest {

    private final BearerTokenFilter filter =
            new BearerTokenFilter(new McpServerProperties("/mcp", "test-token"));

    @Test
    void skipsHealthEndpoint() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Basic dGVzdC10b2tlbg==", "Bearer wrong-token", "Bearer test-token ", "Bearer  test-token"})
    void rejectsMissingMalformedOrWrongToken(String authorization) throws Exception {
        var request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    void acceptsExactBearerToken() throws Exception {
        var request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
```

- [ ] **Step 2: 运行认证测试确认失败**

运行：

```bash
mvn -q -Dtest=BearerTokenFilterTest test
```

预期：FAIL，提示 `McpServerProperties` 或 `BearerTokenFilter` 尚不存在。

- [ ] **Step 3: 实现配置属性**

创建不可变配置类，分别绑定 `app.mcp` 和 `app.query`，并在启动时校验：

```java
@ConfigurationProperties(prefix = "app.mcp")
@Validated
public record McpServerProperties(
        @NotBlank String endpointPath,
        @NotBlank String authToken) {
}

@ConfigurationProperties(prefix = "app.query")
@Validated
public record QueryProperties(
        @Min(1) int maxRows,
        @Min(1024) int maxResultBytes,
        @Min(1) int timeoutSeconds) {
}
```

在启动类上增加 `@ConfigurationPropertiesScan`。空的 `APP_MCP_AUTH_TOKEN` 必须导致生产上下文启动失败；测试通过 `@SpringBootTest(properties=...)` 或 `@DynamicPropertySource` 提供 Token。

- [ ] **Step 4: 实现认证过滤器**

`BearerTokenFilter` 继承 `OncePerRequestFilter`，只对配置的 endpoint path 生效；缺少或不匹配时设置 `401`、`WWW-Authenticate: Bearer` 并停止链路。核心比较逻辑使用：

```java
private boolean hasValidToken(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
        return false;
    }
    byte[] actual = authorization.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
    byte[] expected = properties.authToken().getBytes(StandardCharsets.UTF_8);
    return actual.length == expected.length && MessageDigest.isEqual(actual, expected);
}
```

不要记录 `authorization`，也不要把 Token 放入异常或响应体。`shouldNotFilter` 只跳过不匹配 `/mcp` 的请求，因而 `/actuator/health` 不需要认证。

- [ ] **Step 5: 让配置属性注册并运行测试**

运行：

```bash
mvn -q -Dtest=BearerTokenFilterTest,MysqlMcpServerApplicationTests test
```

预期：所有测试 PASS；未提供 Token 的独立生产启动测试应失败并指出 `app.mcp.auth-token` 校验错误。

---

### Task 3: 以测试驱动实现只读 SQL 校验器

**Files:**
- Create: `src/main/java/com/example/mysqlmcpserver/query/ReadOnlySqlValidator.java`
- Create: `src/main/java/com/example/mysqlmcpserver/query/QueryRejectedException.java`
- Test: `src/test/java/com/example/mysqlmcpserver/query/ReadOnlySqlValidatorTest.java`

- [ ] **Step 1: 写允许语句和拒绝语句的参数化测试**

```java
class ReadOnlySqlValidatorTest {

    private final ReadOnlySqlValidator validator = new ReadOnlySqlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT id, name FROM customers",
            "  select count(*) from customers",
            "SHOW TABLES",
            "DESCRIBE customers",
            "DESC customers",
            "EXPLAIN SELECT * FROM customers",
            "SELECT 'semicolon;inside string'"
    })
    void acceptsSupportedReadOnlyStatements(String sql) {
        assertThatCode(() -> validator.validate(sql)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "INSERT INTO customers VALUES (1, 'x')",
            "UPDATE customers SET name = 'x'",
            "DELETE FROM customers",
            "DROP TABLE customers",
            "ALTER TABLE customers ADD COLUMN x INT",
            "CALL refresh_data()",
            "SET sql_mode = 'x'",
            "SELECT * FROM customers; DELETE FROM customers",
            "SELECT * FROM customers /* hidden */",
            "-- comment\nSELECT * FROM customers",
            "SELECT * FROM customers # comment",
            "SELECT * FROM customers INTO OUTFILE '/tmp/out'",
            "SELECT * FROM customers FOR UPDATE",
            "SELECT * FROM customers LOCK IN SHARE MODE"
    })
    void rejectsStatementsOutsideReadOnlyBoundary(String sql) {
        assertThatThrownBy(() -> validator.validate(sql))
                .isInstanceOf(QueryRejectedException.class);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：

```bash
mvn -q -Dtest=ReadOnlySqlValidatorTest test
```

预期：FAIL，因为校验器和异常尚未实现。

- [ ] **Step 3: 实现词法校验**

`validate(String sql)` 按以下固定顺序执行：

1. `null`、空白或仅注释输入直接拒绝；
2. 单次扫描 SQL，区分普通文本、单引号、双引号和反引号；
3. 在普通文本中发现 `;`、`--`、`#` 或 `/*`/`*/` 立即拒绝；引号内的分号不视为多语句；
4. 读取第一个 SQL 关键字，只允许 `SELECT`、`SHOW`、`DESCRIBE`、`DESC`、`EXPLAIN`；
5. 在普通文本 Token 流中拒绝 `INTO OUTFILE`、`INTO DUMPFILE`、`FOR UPDATE`、`LOCK IN SHARE MODE`、`PROCEDURE`、`CALL`、`SET` 和事务/DDL/写入关键字；
6. 返回规范化但不执行的 SQL。服务层仍通过数据库只读连接执行，校验器不承担数据库权限职责。

扫描器必须只把普通文本中的关键字当作 SQL Token；字符串和反引号内容不能触发关键字拒绝。错误统一使用 `QueryRejectedException`，消息只描述“只允许单条只读查询”，不回显完整 SQL。

- [ ] **Step 4: 运行校验器测试**

运行：

```bash
mvn -q -Dtest=ReadOnlySqlValidatorTest test
```

预期：PASS，所有允许语句被接受，所有写操作、注释、多语句和锁定语句被拒绝。

---

### Task 4: 实现数据库元数据服务

**Files:**
- Create: `src/main/java/com/example/mysqlmcpserver/query/model/TableSummary.java`
- Create: `src/main/java/com/example/mysqlmcpserver/query/model/ColumnDescription.java`
- Create: `src/main/java/com/example/mysqlmcpserver/query/model/TableDescription.java`
- Create: `src/main/java/com/example/mysqlmcpserver/query/model/TableListResult.java`
- Create: `src/main/java/com/example/mysqlmcpserver/query/DatabaseMetadataService.java`
- Create: `src/main/java/com/example/mysqlmcpserver/query/DatabaseResourceNotFoundException.java`
- Create: `src/test/resources/db/init.sql`
- Test: `src/test/java/com/example/mysqlmcpserver/query/DatabaseMetadataServiceTest.java`

- [ ] **Step 1: 创建 Testcontainers 初始化 SQL**

创建 `src/test/resources/db/init.sql`：

```sql
CREATE TABLE customers (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NULL
);

INSERT INTO customers (id, name, created_at) VALUES
    (1, 'Alice', '2026-01-01 00:00:00'),
    (2, 'Bob', '2026-01-02 00:00:00');

CREATE VIEW customer_names AS
SELECT id, name FROM customers;
```

- [ ] **Step 2: 写真实 MySQL 元数据测试**

在 `DatabaseMetadataServiceTest` 中使用：

```java
@Testcontainers
@SpringBootTest(properties = {
        "app.mcp.auth-token=test-token",
        "app.query.max-rows=1000",
        "app.query.max-result-bytes=1048576",
        "app.query.timeout-seconds=10"
})
class DatabaseMetadataServiceTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("app")
            .withUsername("app")
            .withPassword("app")
            .withInitScript("db/init.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    DatabaseMetadataService metadataService;

    @Test
    void listsTablesAndViews() {
        assertThat(metadataService.listTables().tables())
                .extracting(TableSummary::tableName)
                .contains("customers", "customer_names");
    }

    @Test
    void describesColumnsAndPrimaryKey() {
        TableDescription result = metadataService.describeTable("customers");

        assertThat(result.columns()).extracting(ColumnDescription::columnName)
                .containsExactly("id", "name", "created_at");
        assertThat(result.columns().get(0).primaryKeyPosition()).isEqualTo(1);
    }

    @Test
    void reportsMissingTableWithoutExecutingUserSql() {
        assertThatThrownBy(() -> metadataService.describeTable("missing_table"))
                .isInstanceOf(DatabaseResourceNotFoundException.class);
    }
}
```

- [ ] **Step 3: 实现元数据模型和查询**

使用 Java records；对 `table_name`、`table_type`、`row_count`、`elapsed_ms` 等 MCP 字段使用显式 `@JsonProperty`，不依赖全局 Jackson 命名策略。核心 SQL 为：

```sql
SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE
FROM information_schema.tables
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME
```

表结构查询使用参数绑定：

```sql
SELECT c.COLUMN_NAME, c.DATA_TYPE, c.COLUMN_TYPE, c.IS_NULLABLE,
       c.COLUMN_DEFAULT, c.CHARACTER_SET_NAME, c.COLLATION_NAME,
       k.ORDINAL_POSITION AS PRIMARY_KEY_POSITION
FROM information_schema.columns c
LEFT JOIN information_schema.key_column_usage k
  ON k.TABLE_SCHEMA = c.TABLE_SCHEMA
 AND k.TABLE_NAME = c.TABLE_NAME
 AND k.COLUMN_NAME = c.COLUMN_NAME
 AND k.CONSTRAINT_NAME = 'PRIMARY'
WHERE c.TABLE_SCHEMA = DATABASE()
  AND c.TABLE_NAME = ?
ORDER BY c.ORDINAL_POSITION
```

`DatabaseMetadataService.describeTable(tableName)` 对空名称拒绝；查询无列时抛出内部 `DatabaseResourceNotFoundException`，不把输入直接拼接到 SQL。`listTables()` 返回 `TableListResult`，并按名称排序。

- [ ] **Step 4: 运行元数据集成测试**

运行：

```bash
mvn -q -Dtest=DatabaseMetadataServiceTest test
```

预期：Testcontainers 启动 MySQL 8.4，表/视图发现、列结构、主键和不存在表场景全部 PASS。

---

### Task 5: 实现受资源限制的只读查询服务

**Files:**
- Create: `src/main/java/com/example/mysqlmcpserver/query/QueryResult.java`
- Create: `src/main/java/com/example/mysqlmcpserver/query/ReadOnlyQueryService.java`
- Create: `src/main/java/com/example/mysqlmcpserver/query/DatabaseQueryException.java`
- Create: `src/main/java/com/example/mysqlmcpserver/query/DatabaseQueryTimeoutException.java`
- Modify: `src/test/java/com/example/mysqlmcpserver/query/ReadOnlyQueryServiceTest.java`

- [ ] **Step 1: 写查询结果、截断和错误测试**

增加以下测试：

```java
@Autowired
ReadOnlyQueryService queryService;

@Test
void executesSelectAndReturnsColumnNamesAndRows() {
    QueryResult result = queryService.execute("SELECT id, name FROM customers ORDER BY id", 100);

    assertThat(result.columns()).containsExactly("id", "name");
    assertThat(result.rows()).hasSize(2);
    assertThat(result.rowCount()).isEqualTo(2);
    assertThat(result.truncated()).isFalse();
    assertThat(result.elapsedMs()).isGreaterThanOrEqualTo(0);
}

@Test
void capsRequestedRowsAtConfiguredMaximum() {
    QueryResult result = queryService.execute("SELECT id, name FROM customers ORDER BY id", 10_000);

    assertThat(result.rows()).hasSizeLessThanOrEqualTo(1000);
}

@Test
void rejectsReadOnlyViolationBeforeJdbcExecution() {
    assertThatThrownBy(() -> queryService.execute("DELETE FROM customers", 10))
            .isInstanceOf(QueryRejectedException.class);
}

@Test
void appliesResultByteLimitAndMarksTruncation() {
    QueryResult result = queryService.execute("SELECT id, name FROM customers ORDER BY id", 1);

    assertThat(result.truncated()).isTrue();
    assertThat(result.rows()).hasSizeLessThanOrEqualTo(1);
}
```

为验证硬上限，可在测试类上使用 `@TestPropertySource(properties = "app.query.max-rows=1")` 的独立测试上下文，避免修改全局配置。

- [ ] **Step 2: 运行测试确认查询服务尚未实现**

运行：

```bash
mvn -q -Dtest=ReadOnlyQueryServiceTest test
```

预期：FAIL，提示 `ReadOnlyQueryService` 或 `QueryResult` 尚不存在。

- [ ] **Step 3: 实现 JDBC 资源限制**

`ReadOnlyQueryService.execute(sql, requestedMaxRows)` 按以下顺序实现：

1. 调用 `ReadOnlySqlValidator.validate(sql)`；
2. 计算 `effectiveMaxRows = min(requestedMaxRows 或 configuredMaxRows, configuredMaxRows)`；
3. 使用 `JdbcTemplate.query(PreparedStatementCreator, ResultSetExtractor<QueryResult>)`，不要使用 `queryForList`，避免先把无限结果全部加载到内存；
4. 在 `PreparedStatementCreator` 中调用 `setQueryTimeout(timeoutSeconds)`、`setMaxRows(effectiveMaxRows + 1)`，并通过数据源的 Hikari `read-only` 配置维持只读连接；
5. 从 `ResultSetMetaData` 构造列名；每行使用 `LinkedHashMap<String,Object>` 保持列顺序；
6. 用 Jackson 序列化候选行估算返回字节数，超过 `maxResultBytes` 时停止加入该行并设置 `truncated=true`；
7. 如果读取到第 `effectiveMaxRows + 1` 行，只保留前 `effectiveMaxRows` 行并设置 `truncated=true`；
8. 记录 `System.nanoTime()` 前后的耗时；
9. 将 JDBC 超时映射为 `DatabaseQueryTimeoutException`，其他 `DataAccessException` 映射为安全的 `DatabaseQueryException`，不把 SQL 或连接信息写入异常消息。

`QueryResult` 使用：

```java
public record QueryResult(
        List<String> columns,
        List<Map<String, Object>> rows,
        int rowCount,
        boolean truncated,
        long elapsedMs) {
}
```

结果转换必须保持 JSON 可序列化；`byte[]` 转为 Base64 字符串，日期/时间和数字保留为 Jackson 可序列化的值，不能把 `ResultSet`、`Connection` 或 JDBC statement 放入返回模型。

- [ ] **Step 4: 运行查询服务集成测试**

运行：

```bash
mvn -q -Dtest=ReadOnlyQueryServiceTest test
```

预期：真实 MySQL 查询、只读拒绝、请求上限收敛和结果字节截断全部 PASS。

---

### Task 6: 注册 MCP 工具并转换安全错误

**Files:**
- Create: `src/main/java/com/example/mysqlmcpserver/config/McpToolConfiguration.java`
- Create: `src/main/java/com/example/mysqlmcpserver/query/ReadOnlyDatabaseTools.java`
- Modify: `src/test/java/com/example/mysqlmcpserver/MysqlMcpServerApplicationTests.java`

- [ ] **Step 1: 写工具 Bean 注册测试**

在上下文测试中注入 `ToolCallbackProvider`，断言工具名称和数量：

```java
@Autowired
private ToolCallbackProvider toolCallbackProvider;

@Test
void exposesReadOnlyDatabaseTools() {
    assertThat(toolCallbackProvider.getToolCallbacks())
            .extracting(callback -> callback.getToolDefinition().name())
            .containsExactlyInAnyOrder("list_tables", "describe_table", "execute_read_query");
}
```

- [ ] **Step 2: 运行测试确认工具尚未注册**

运行：

```bash
mvn -q -Dtest=MysqlMcpServerApplicationTests test
```

预期：FAIL，因为没有 `ToolCallbackProvider` Bean。

- [ ] **Step 3: 实现工具入口和注册 Bean**

`ReadOnlyDatabaseTools` 使用 Spring AI `@Tool`，方法只做参数边界检查、调用服务和异常转换：

```java
@Component
public final class ReadOnlyDatabaseTools {

    private final DatabaseMetadataService metadataService;
    private final ReadOnlyQueryService queryService;

    @Tool(name = "list_tables", description = "List tables and views in the current MySQL database. Read-only.")
    public TableListResult listTables() {
        return metadataService.listTables();
    }

    @Tool(name = "describe_table", description = "Describe columns and primary keys for a table or view. Read-only.")
    public TableDescription describeTable(String tableName) {
        return metadataService.describeTable(tableName);
    }

    @Tool(name = "execute_read_query", description = "Execute one read-only SELECT, SHOW, DESCRIBE, DESC, or EXPLAIN statement.")
    public QueryResult executeReadQuery(String sql, Integer maxRows) {
        return queryService.execute(sql, maxRows);
    }
}
```

方法签名和工具描述必须明确说明不支持写操作。工具层只传播内部安全异常：`QueryRejectedException`、`DatabaseResourceNotFoundException`、`DatabaseQueryException` 和 `DatabaseQueryTimeoutException` 的消息分别固定为“只允许单条只读查询”“表或视图不存在”“数据库查询失败”“查询超时”，使 Spring AI 的工具执行层可以将其作为 MCP tool error 返回而不暴露底层异常。不要回显密码、连接 URL、堆栈或完整 SQL。

`McpToolConfiguration` 使用 Spring AI 的方法工具回调注册：

```java
@Configuration
public class McpToolConfiguration {

    @Bean
    ToolCallbackProvider databaseTools(ReadOnlyDatabaseTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
```

- [ ] **Step 4: 运行上下文和回调注册测试**

运行：

```bash
mvn -q -Dtest=MysqlMcpServerApplicationTests test
```

预期：上下文启动成功，并发现且只注册三个工具。

---

### Task 7: 验证 Streamable HTTP 和认证链路

**Files:**
- Modify: `src/test/java/com/example/mysqlmcpserver/MysqlMcpServerApplicationTests.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: 添加 HTTP 认证测试**

使用 `@AutoConfigureMockMvc` 和 Testcontainers 上下文，增加：

```java
@Autowired
private MockMvc mockMvc;

@Test
void rejectsMcpRequestWithoutToken() throws Exception {
    mockMvc.perform(post("/mcp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isUnauthorized());
}

@Test
void rejectsMcpRequestWithWrongToken() throws Exception {
    mockMvc.perform(post("/mcp")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isUnauthorized());
}

@Test
void acceptsInitializeRequestWithCorrectToken() throws Exception {
    String initialize = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
              "protocolVersion":"2025-03-26",
              "capabilities":{},
              "clientInfo":{"name":"integration-test","version":"1.0"}
            }}
            """;

    mockMvc.perform(post("/mcp")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                    .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(initialize))
            .andExpect(status().is2xxSuccessful())
            .andExpect(content().string(containsString("serverInfo")));
}
```

如果所选 Spring AI 版本对初始化协议版本返回 SSE 而不是普通 JSON，断言保持为 2xx、响应包含 `serverInfo`，不要把测试绑定到传输格式细节。

- [ ] **Step 2: 运行 HTTP 测试确认传输配置**

运行：

```bash
mvn -q -Dtest=MysqlMcpServerApplicationTests test
```

预期：无 Token 和错误 Token 返回 401；正确 Token 能通过过滤器并得到 MCP 初始化成功响应。若正确 Token 请求返回 404 或 405，修正 `application.yml` 中与 Spring AI 1.0.0 对应的 Streamable HTTP endpoint 属性，而不是新增控制器。

- [ ] **Step 3: 验证健康检查不受认证影响**

增加测试：

```java
@Test
void healthEndpointDoesNotRequireMcpToken() throws Exception {
    mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
}
```

运行：

```bash
mvn -q -Dtest=MysqlMcpServerApplicationTests#healthEndpointDoesNotRequireMcpToken test
```

预期：返回 200，响应不包含数据库密码或 MCP Token。

---

### Task 8: 补充使用文档并执行完整验证

**Files:**
- Create: `README.md`
- Modify: `src/main/resources/application.yml`（仅在前述集成测试发现属性需要调整时）

- [ ] **Step 1: 编写 README 配置和启动说明**

README 至少包含以下可直接复制的无密钥示例：

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/app?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
export SPRING_DATASOURCE_USERNAME='mcp_reader'
export SPRING_DATASOURCE_PASSWORD='<database-password>'
export APP_MCP_AUTH_TOKEN='<long-random-token>'

mvn spring-boot:run
```

文档明确 MCP 地址为 `http://localhost:8090/mcp`，请求必须携带 `Authorization: Bearer <APP_MCP_AUTH_TOKEN>`，并列出三个工具及其只读边界。加入最小权限示例：

```sql
CREATE USER 'mcp_reader'@'%' IDENTIFIED BY '<database-password>';
GRANT SELECT, SHOW VIEW ON `app`.* TO 'mcp_reader'@'%';
```

文档还要说明最大行数、最大结果字节数、查询超时的配置键，以及不要把真实密码或 Token 提交到仓库。

- [ ] **Step 2: 运行完整测试与打包**

运行：

```bash
mvn verify
```

预期：Testcontainers MySQL 8.4 测试全部通过，Maven 输出 `BUILD SUCCESS`，并生成可运行的 Spring Boot jar。

- [ ] **Step 3: 执行最终安全检查**

运行：

```bash
git diff --check
git status --short
```

检查结果必须确认：

- 没有提交真实数据库密码、Token 或 `.env` 文件；
- 没有新增写数据库的 JDBC 调用；
- `/mcp` 的无 Token、错误 Token 请求都在进入 MCP 工具前返回 401；
- 所有工具回调名称恰好是 `list_tables`、`describe_table`、`execute_read_query`；
- `mvn verify` 已在 Testcontainers MySQL 8.4 上通过。

除非用户另行要求，不在此阶段自动创建 git commit 或推送远端。
