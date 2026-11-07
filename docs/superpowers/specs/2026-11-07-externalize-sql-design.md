# 将固定 SQL 外置到 classpath 资源

## 背景

`DatabaseMetadataService` 当前将数据库元数据查询以 Java 文本块常量保存。后续会持续增加由开发者随代码发布的固定查询，需要避免 Service 类随 SQL 数量增长而变得臃肿，同时保持 SQL 与应用版本一致。

## 目标

- 将固定查询移到 `src/main/resources/sql`；
- SQL 随 Spring Boot JAR 一起发布，并纳入 Git 版本管理；
- 保持现有查询语义、结果映射和参数绑定行为不变；
- SQL 资源缺失或读取失败时，让应用在启动阶段失败，而不是运行到对应功能时才失败。

## 非目标

- 不支持部署人员在不重新打包的情况下修改或新增 SQL；
- 不引入数据库存储过程、动态 SQL 配置或通用 SQL 管理平台；
- 不改变只读权限、查询校验或 MCP 工具接口。

## 方案

每条固定查询对应一个 classpath 下的 `.sql` 文件，例如：

```text
src/main/resources/sql/metadata-list-tables.sql
src/main/resources/sql/metadata-describe-table.sql
```

应用启动时加载这些资源，并将加载后的 SQL 注入 `DatabaseMetadataService`。Service 只负责执行查询、绑定参数和映射结果，不再保存 SQL 文本。

资源读取失败应转换为启动阶段异常。SQL 文件中的 `?` 占位符必须保留，`tableName` 继续通过 `JdbcTemplate` 的参数绑定传入，不能使用字符串拼接。

## 测试

- 保留现有集成测试和 Testcontainers MySQL 配置；
- 增加或调整测试，验证应用上下文能够加载 classpath SQL 资源；
- 通过现有表结构查询路径验证迁移后 `list_tables` 和 `describe_table` 的行为未改变；
- 验证参数化查询仍能正确描述指定表。

## 验收标准

- `DatabaseMetadataService` 中不再包含这两条 SQL 的正文；
- 两个 SQL 文件位于 `src/main/resources/sql` 并会被 Maven/Spring Boot 打包；
- 应用正常启动，现有测试通过；
- SQL 资源缺失时应用无法正常启动；
- 查询参数没有退化为字符串拼接。
