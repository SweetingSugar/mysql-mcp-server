package com.example.mysqlmcpserver.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TableDescription(
        @JsonProperty("schema") String schema,
        @JsonProperty("table_name") String tableName,
        @JsonProperty("table_type") String tableType,
        @JsonProperty("columns") List<ColumnDescription> columns) {
}
