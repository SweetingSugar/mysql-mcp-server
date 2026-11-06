package com.example.mysqlmcpserver.config;

import com.example.mysqlmcpserver.query.ReadOnlyDatabaseTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfiguration {

    @Bean
    ToolCallbackProvider databaseTools(ReadOnlyDatabaseTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
