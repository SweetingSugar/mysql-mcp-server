package com.example.mysqlmcpserver.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TableSummary(
        @JsonProperty("schema") String schema,
        @JsonProperty("table_name") String tableName,
        @JsonProperty("table_type") String tableType) {
}
