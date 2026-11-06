package com.example.mysqlmcpserver.query;

import com.example.mysqlmcpserver.config.QueryProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReadOnlyQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final QueryProperties properties;
    private final ReadOnlySqlValidator validator;
    private final ObjectMapper objectMapper;

    public ReadOnlyQueryService(
            JdbcTemplate jdbcTemplate,
            QueryProperties properties,
            ReadOnlySqlValidator validator,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    public QueryResult execute(String sql, Integer requestedMaxRows) {
        String validatedSql = validator.validate(sql);
        int maxRows = effectiveMaxRows(requestedMaxRows);
        long startedAt = System.nanoTime();

        try {
            ResultSetExtractor<QueryResult> resultExtractor =
                    resultSet -> extractResult(resultSet, maxRows, startedAt);
            return jdbcTemplate.query(
                    connection -> createStatement(connection, validatedSql, maxRows),
                    resultExtractor);
        }
        catch (QueryTimeoutException exception) {
            throw new DatabaseQueryTimeoutException(exception);
        }
        catch (DataAccessException exception) {
            if (containsTimeout(exception)) {
                throw new DatabaseQueryTimeoutException(exception);
            }
            throw new DatabaseQueryException(exception);
        }
    }

    private PreparedStatement createStatement(
            java.sql.Connection connection,
            String sql,
            int maxRows) throws SQLException {
        connection.setReadOnly(true);
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(properties.timeoutSeconds());
        statement.setMaxRows(maxRows == Integer.MAX_VALUE ? maxRows : maxRows + 1);
        return statement;
    }

    private QueryResult extractResult(ResultSet resultSet, int maxRows, long startedAt) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        List<String> columns = new ArrayList<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            columns.add(metadata.getColumnLabel(index));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        long resultBytes = 0;
        boolean truncated = false;
        int scannedRows = 0;
        while (resultSet.next()) {
            scannedRows++;
            if (scannedRows > maxRows) {
                truncated = true;
                break;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                row.put(columns.get(index - 1), jsonValue(resultSet.getObject(index)));
            }

            long rowBytes = serializedSize(row);
            if (resultBytes + rowBytes > properties.maxResultBytes()) {
                truncated = true;
                break;
            }
            rows.add(row);
            resultBytes += rowBytes;
        }

        return new QueryResult(columns, List.copyOf(rows), rows.size(), truncated, elapsedMillis(startedAt));
    }

    private Object jsonValue(Object value) throws SQLException {
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof java.sql.Blob blob) {
            long length = blob.length();
            if (length > Integer.MAX_VALUE) {
                throw new SQLException("Blob value is too large");
            }
            return Base64.getEncoder().encodeToString(blob.getBytes(1, (int) length));
        }
        if (value instanceof java.sql.Clob clob) {
            long length = clob.length();
            if (length > Integer.MAX_VALUE) {
                throw new SQLException("Clob value is too large");
            }
            return clob.getSubString(1, (int) length);
        }
        return value;
    }

    private long serializedSize(Map<String, Object> row) {
        try {
            return objectMapper.writeValueAsBytes(row).length;
        }
        catch (JsonProcessingException exception) {
            throw new DatabaseQueryException(exception);
        }
    }

    private int effectiveMaxRows(Integer requestedMaxRows) {
        if (requestedMaxRows != null && requestedMaxRows < 1) {
            throw new QueryRejectedException();
        }
        return Math.min(
                requestedMaxRows == null ? properties.maxRows() : requestedMaxRows,
                properties.maxRows());
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private boolean containsTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
