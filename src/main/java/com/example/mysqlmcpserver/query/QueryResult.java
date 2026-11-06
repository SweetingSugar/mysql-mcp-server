package com.example.mysqlmcpserver.query;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record QueryResult(
        @JsonProperty("columns") List<String> columns,
        @JsonProperty("rows") List<Map<String, Object>> rows,
        @JsonProperty("row_count") int rowCount,
        @JsonProperty("truncated") boolean truncated,
        @JsonProperty("elapsed_ms") long elapsedMs) {
}
