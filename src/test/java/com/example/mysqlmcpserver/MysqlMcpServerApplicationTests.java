package com.example.mysqlmcpserver;

import org.junit.jupiter.api.Test;
import com.example.mysqlmcpserver.query.ReadOnlySqlValidator;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.mcp.auth-token=test-token",
        "app.query.max-rows=1000",
        "app.query.max-result-bytes=1048576",
        "app.query.timeout-seconds=10"
})
class MysqlMcpServerApplicationTests {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("app")
            .withUsername("app")
            .withPassword("app")
            .withInitScript("db/init.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private ToolCallbackProvider toolCallbackProvider;

    @Autowired
    private ReadOnlySqlValidator readOnlySqlValidator;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void registersReadOnlySqlValidatorAsBean() {
        assertThat(readOnlySqlValidator).isNotNull();
    }

    @Test
    void exposesReadOnlyDatabaseTools() {
        assertThat(toolCallbackProvider.getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder("list_tables", "describe_table", "execute_read_query");
    }

    @Test
    void acceptsInitializeRequestWithCorrectToken() throws Exception {
        String initialize = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-03-26",
                  "capabilities":{},
                  "clientInfo":{"name":"integration-test","version":"1.0"}
                }}
                """;

        mockMvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialize))
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().string(containsString("serverInfo")));
    }

    @Test
    void rejectsMcpRequestWithoutToken() throws Exception {
        mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMcpRequestWithWrongToken() throws Exception {
        mockMvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointDoesNotRequireMcpToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

}
