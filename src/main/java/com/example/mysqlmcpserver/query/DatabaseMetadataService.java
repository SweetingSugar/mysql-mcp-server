package com.example.mysqlmcpserver.query;

import com.example.mysqlmcpserver.query.model.ColumnDescription;
import com.example.mysqlmcpserver.query.model.TableDescription;
import com.example.mysqlmcpserver.query.model.TableListResult;
import com.example.mysqlmcpserver.query.model.TableSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class DatabaseMetadataService {

    private final JdbcTemplate jdbcTemplate;
    private final String listTablesSql;
    private final String describeTableSql;

    public DatabaseMetadataService(
            JdbcTemplate jdbcTemplate,
            @Value("classpath:sql/metadata-list-tables.sql") Resource listTablesResource,
            @Value("classpath:sql/metadata-describe-table.sql") Resource describeTableResource) {
        this.jdbcTemplate = jdbcTemplate;
        this.listTablesSql = readSql(listTablesResource);
        this.describeTableSql = readSql(describeTableResource);
    }

    public TableListResult listTables() {
        List<TableSummary> tables = jdbcTemplate.query(
                listTablesSql,
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
