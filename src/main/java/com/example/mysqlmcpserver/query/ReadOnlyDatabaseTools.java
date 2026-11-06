package com.example.mysqlmcpserver.query;

import com.example.mysqlmcpserver.query.model.TableDescription;
import com.example.mysqlmcpserver.query.model.TableListResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.dao.DataAccessException;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public final class ReadOnlyDatabaseTools {

    private final DatabaseMetadataService metadataService;
    private final ReadOnlyQueryService queryService;

    public ReadOnlyDatabaseTools(
            DatabaseMetadataService metadataService,
            ReadOnlyQueryService queryService) {
        this.metadataService = metadataService;
        this.queryService = queryService;
    }

    @Tool(
            name = "list_tables",
            description = "List tables and views in the current MySQL database. This operation is read-only.")
    public TableListResult listTables() {
        try {
            return metadataService.listTables();
        }
        catch (DataAccessException exception) {
            throw new DatabaseQueryException(exception);
        }
    }

    @Tool(
            name = "describe_table",
            description = "Describe columns and primary keys for a table or view in the current MySQL database. This operation is read-only.")
    public TableDescription describeTable(
            @ToolParam(description = "The exact table or view name") String tableName) {
        try {
            return metadataService.describeTable(tableName);
        }
        catch (DataAccessException exception) {
            throw new DatabaseQueryException(exception);
        }
    }

    @Tool(
            name = "execute_read_query",
            description = "Execute exactly one read-only SELECT, SHOW, DESCRIBE, DESC, or EXPLAIN statement. Write operations, multiple statements, comments, and locking queries are rejected.")
    public QueryResult executeReadQuery(
            @ToolParam(description = "One read-only SQL statement") String sql,
            @ToolParam(description = "Optional maximum number of rows; it cannot exceed the server limit", required = false)
            Integer maxRows) {
        return queryService.execute(sql, maxRows);
    }
}
