package com.example.mysqlmcpserver.query;

import com.example.mysqlmcpserver.query.model.ColumnDescription;
import com.example.mysqlmcpserver.query.model.TableDescription;
import com.example.mysqlmcpserver.query.model.TableListResult;
import com.example.mysqlmcpserver.query.model.TableSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DatabaseMetadataService {

    private static final String LIST_TABLES_SQL = """
            SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE
            FROM information_schema.tables
            WHERE TABLE_SCHEMA = DATABASE()
            ORDER BY TABLE_NAME
            """;

    private static final String DESCRIBE_TABLE_SQL = """
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
            """;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMetadataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TableListResult listTables() {
        List<TableSummary> tables = jdbcTemplate.query(
                LIST_TABLES_SQL,
                (resultSet, rowNum) -> new TableSummary(
                        resultSet.getString("TABLE_SCHEMA"),
                        resultSet.getString("TABLE_NAME"),
                        resultSet.getString("TABLE_TYPE")));
        return new TableListResult(List.copyOf(tables));
    }

    public TableDescription describeTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new DatabaseResourceNotFoundException();
        }

        List<TableRow> rows = jdbcTemplate.query(
                DESCRIBE_TABLE_SQL,
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

        if (rows.isEmpty()) {
            throw new DatabaseResourceNotFoundException();
        }

        TableRow first = rows.get(0);
        return new TableDescription(
                first.schema(),
                first.tableName(),
                first.tableType(),
                rows.stream().map(TableRow::column).toList());
    }

    private Integer primaryKeyPosition(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        int position = resultSet.getInt("PRIMARY_KEY_POSITION");
        return resultSet.wasNull() ? null : position;
    }

    private record TableRow(
            String schema,
            String tableName,
            String tableType,
            ColumnDescription column) {
    }
}
