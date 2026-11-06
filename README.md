# MySQL MCP Server

基于 Spring Boot、Spring AI MCP Server 和 Spring JDBC 的只读 MySQL MCP 服务，通过 Streamable HTTP 为外部 AI 模型提供数据库结构发现和查询能力。

## 功能

MCP 端点：`http://localhost:8090/mcp`

- `list_tables`：列出当前数据库中的表和视图；
- `describe_table`：查看表或视图的列、类型、可空性、默认值、字符集、排序规则和主键信息；
- `execute_read_query`：执行单条 `SELECT`、`SHOW`、`DESCRIBE`、`DESC` 或 `EXPLAIN` 查询。

服务拒绝写操作、DDL、事务控制、存储过程、多语句、注释和锁定查询。数据库账号也必须使用只读权限。

## 配置和启动

需要 Java 17+、Maven 和可访问的 MySQL 8.x 实例。

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/app?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
export SPRING_DATASOURCE_USERNAME='mcp_reader'
export SPRING_DATASOURCE_PASSWORD='<database-password>'
export APP_MCP_AUTH_TOKEN='<long-random-token>'

mvn spring-boot:run
```

MCP 客户端请求必须携带：

```text
Authorization: Bearer <APP_MCP_AUTH_TOKEN>
```

健康检查地址为 `http://localhost:8090/actuator/health`，不要求 MCP Token。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/app?...` | MySQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `app` | 数据库用户 |
| `SPRING_DATASOURCE_PASSWORD` | 空 | 数据库密码 |
| `APP_MCP_AUTH_TOKEN` | 无 | MCP Bearer Token；生产环境必须设置 |
| `APP_QUERY_MAX_ROWS` | `1000` | 单次查询最大返回行数 |
| `APP_QUERY_MAX_RESULT_BYTES` | `1048576` | 单次查询最大结果字节数 |
| `APP_QUERY_TIMEOUT_SECONDS` | `10` | 单次查询超时时间 |

`max_rows` 工具参数只能进一步降低服务端最大行数，不能提高它。敏感值应通过环境变量或密钥管理系统注入，不要提交到仓库。

## MySQL 最小权限

为 MCP 服务创建只读账号，并只授予目标数据库的查询和查看视图权限：

```sql
CREATE USER 'mcp_reader'@'%' IDENTIFIED BY '<database-password>';
GRANT SELECT, SHOW VIEW ON `app`.* TO 'mcp_reader'@'%';
```

不要向该账号授予 `INSERT`、`UPDATE`、`DELETE`、DDL、`FILE` 或管理权限。应用层 SQL 校验不能替代数据库权限控制。

## 测试

测试使用 Testcontainers 自动启动 MySQL 8.4，不依赖本机预先运行的数据库：

```bash
mvn verify
```
