package com.example.mysqlmcpserver.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ColumnDescription(
        @JsonProperty("column_name") String columnName,
        @JsonProperty("data_type") String dataType,
        @JsonProperty("column_type") String columnType,
        @JsonProperty("is_nullable") String nullable,
        @JsonProperty("column_default") Object defaultValue,
        @JsonProperty("character_set_name") String characterSetName,
        @JsonProperty("collation_name") String collationName,
        @JsonProperty("primary_key_position") Integer primaryKeyPosition) {
}
