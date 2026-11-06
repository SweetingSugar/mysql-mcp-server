package com.example.mysqlmcpserver.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.mcp")
@Validated
public record McpServerProperties(
        @NotBlank String endpointPath,
        @NotBlank String authToken) {
}
