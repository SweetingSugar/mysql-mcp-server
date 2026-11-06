# MySQL MCP Server 设计规格

日期：2026-11-06

## 1. 目标与范围

在现有 Spring Boot 3.x 空骨架上实现一个 Java 17+ MCP 服务，供外部 AI 模型通过 Streamable HTTP 查询 MySQL 8.x 数据。服务使用 Spring AI MCP Server 处理 MCP 协议和会话生命周期，使用 Spring JDBC 访问数据库，使用 Maven 构建。

第一版只提供数据库只读能力，不提供任何数据写入、结构变更或事务控制能力，也不增加前端功能。

## 2. 方案选择

采用 Spring AI MCP Server WebMVC 自动配置方案：

- 使用 Spring AI MCP Server WebMVC starter 提供 Streamable HTTP、MCP 初始化、工具发现和 JSON-RPC 生命周期；
- 使用 Spring AI 工具 Bean 暴露查询能力；
- 使用 `JdbcTemplate` 执行参数化元数据查询和只读 SQL；
- 使用应用过滤器保护 MCP HTTP 端点；
- 使用 Testcontainers MySQL 8.x 验证真实 JDBC 行为。

不手写 MCP 协议控制器，不引入固定业务查询清单，也不实现写操作。

## 3. 系统结构

### 3.1 组件

- `MysqlMcpServerApplication`：Spring Boot 启动入口；
- MCP Server 配置：启用 WebMVC Streamable HTTP，端点为 `/mcp`；
- `ReadOnlyDatabaseTools`：面向 MCP 的三个工具入口和工具描述；
- 数据库服务层：封装表、列、主键元数据查询及只读 SQL 执行；
- 只读 SQL 校验器：在执行前验证查询边界；
- 结果限制器：执行查询超时、最大行数和最大返回字节数限制；
- Bearer Token 过滤器：只保护 `/mcp`，使用配置中的 Token 做常量时间比较；
- 配置属性：绑定数据库、认证和查询限制配置，并在启动时校验必填项。

### 3.2 数据流

1. MCP 客户端向 `/mcp` 发送带 Bearer Token 的 Streamable HTTP 请求；
2. 认证过滤器验证 Authorization 头；
3. Spring AI MCP Server 解析 MCP 请求并路由到工具；
4. 工具服务校验工具参数和查询限制；
5. `JdbcTemplate` 使用数据源访问 MySQL；
6. 工具将结构化结果或安全的工具错误返回给 MCP Server；
7. MCP Server 以 Streamable HTTP 响应客户端。

## 4. MCP 工具契约

### 4.1 `list_tables`

无参数。查询当前连接数据库中的表和视图，返回对象名称、对象类型和 schema。

### 4.2 `describe_table`

参数：

- `table_name`：当前数据库中的表或视图名称。

通过 `information_schema` 查询列名、数据类型、完整类型、可空性、默认值、字符集、排序规则和主键序号。输入名称使用参数绑定查询，不直接拼接到 SQL 中；不存在的对象返回工具错误。

### 4.3 `execute_read_query`

参数：

- `sql`：单条只读 SQL；
- `max_rows`：可选的请求行数上限，只能低于或等于服务端配置上限。

允许的语句类型为单条 `SELECT`、`SHOW`、`DESCRIBE`/`DESC` 和 `EXPLAIN`。拒绝空语句、多语句、注释绕过、存储过程调用、事务控制、数据写入、DDL 和其他非只读语句。服务端数据库账号必须额外限制为只读权限，应用层校验不是数据库权限的替代品。

返回统一结构：

- `columns`：列名列表；
- `rows`：行数据；
- `row_count`：实际返回行数；
- `truncated`：是否因行数或大小限制截断；
- `elapsed_ms`：数据库执行耗时。

不返回 JDBC 连接信息、数据库密码、认证 Token 或异常堆栈。

## 5. 安全与资源限制

### 5.1 HTTP 认证

- MCP 端点要求 `Authorization: Bearer <token>`；
- Token 通过配置绑定，缺失时应用启动失败；
- 缺少、格式错误或不匹配的 Token 返回 HTTP 401；
- Token 比较使用 `MessageDigest.isEqual`；
- 健康检查不要求 Bearer Token；
- 日志不记录 Token 或完整 Authorization 头。

### 5.2 查询安全

- 元数据查询全部使用参数绑定；
- 查询校验器拒绝多语句和非允许语句类型；
- 数据源连接设置为只读，并要求 MySQL 账号只授予 `SELECT` 和必要的元数据权限；
- SQL 语法错误、权限错误和超时转为不泄露内部细节的工具错误；
- 不把用户提交的完整 SQL 写入普通日志。

### 5.3 默认限制

第一版使用以下默认值，并允许通过配置覆盖：

- 最大返回行数：1000；
- 最大结果大小：1 MiB；
- 查询超时：10 秒。

请求参数不得突破服务端限制。达到限制时保留已取得的合法结果并设置 `truncated=true`；数据库执行失败时返回错误而不是部分成功结果。

## 6. 配置

配置通过 Spring Boot 标准环境变量覆盖：

- `SPRING_DATASOURCE_URL`；
- `SPRING_DATASOURCE_USERNAME`；
- `SPRING_DATASOURCE_PASSWORD`；
- `APP_MCP_AUTH_TOKEN`；
- `APP_QUERY_MAX_ROWS`；
- `APP_QUERY_MAX_RESULT_BYTES`；
- `APP_QUERY_TIMEOUT_SECONDS`。

MCP Streamable HTTP、端点路径和 Spring AI MCP Server 属性写入 `application.yml`。敏感值不提交到仓库。README 说明启动命令、MCP 客户端连接地址和 MySQL 最小权限配置示例。

## 7. 错误处理

工具错误按以下类别转换：

- 参数为空或非法：参数错误；
- 表或视图不存在：资源不存在；
- 非只读 SQL、多语句或超过限制：查询被拒绝；
- SQL 语法、权限或连接错误：数据库查询失败；
- 查询超时：查询超时。

对外消息只包含类别和可操作的简短说明；服务端日志保留异常类型、请求关联信息和耗时，不暴露凭据、连接 URL 中的敏感部分或完整堆栈给 MCP 客户端。

## 8. 测试策略

使用 Testcontainers 启动 MySQL 8.x，并在测试中创建最小测试 schema 和数据：

1. Spring 上下文测试验证 MCP Server、JDBC 和认证配置能够启动；
2. 工具测试验证列出表、查看结构、正常只读查询、空结果和结果截断；
3. 查询安全测试验证写操作、多语句、注释绕过、空 SQL 和超出限制的请求均被拒绝；
4. HTTP 集成测试验证缺少 Token、错误 Token 和正确 Token 对 `/mcp` 的响应；
5. Maven `verify` 执行完整测试与构建验证。

测试不依赖开发机上预先运行的 MySQL 实例，测试容器由 Testcontainers 管理。

## 9. 非目标

- 不支持 INSERT、UPDATE、DELETE、DDL、事务和存储过程；
- 不实现多租户、用户级权限模型或 OAuth/OIDC；
- 不实现分页协议之外的异步任务队列；
- 不增加 Web 管理界面；
- 不手写 MCP 协议实现。

## 10. 完成标准

- Maven 构建通过，测试覆盖上述工具、安全和 HTTP 场景；
- 外部 MCP 客户端能够通过 Streamable HTTP 发现并调用三个工具；
- 缺少或错误 Bearer Token 时不能调用 MCP 工具；
- 所有数据库查询路径均为只读，并受行数、结果大小和超时限制；
- README 能让使用者仅通过环境变量完成配置和启动。
