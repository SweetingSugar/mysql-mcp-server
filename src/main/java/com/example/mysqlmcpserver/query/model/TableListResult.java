package com.example.mysqlmcpserver.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TableListResult(
        @JsonProperty("tables") List<TableSummary> tables) {
}
