package com.example.mysqlmcpserver.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.query")
@Validated
public record QueryProperties(
        @Min(1) int maxRows,
        @Min(1024) int maxResultBytes,
        @Min(1) int timeoutSeconds) {
}
