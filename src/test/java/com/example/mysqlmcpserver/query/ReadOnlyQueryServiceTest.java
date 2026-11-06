package com.example.mysqlmcpserver.query;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
        "app.mcp.auth-token=test-token",
        "app.query.max-rows=1",
        "app.query.max-result-bytes=1024",
        "app.query.timeout-seconds=10"
})
class ReadOnlyQueryServiceTest {

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
    ReadOnlyQueryService queryService;

    @Test
    void executesSelectAndReturnsColumnNamesAndRows() {
        QueryResult result = queryService.execute("SELECT id, name FROM customers ORDER BY id", 1);

        assertThat(result.columns()).containsExactly("id", "name");
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.truncated()).isTrue();
        assertThat(result.elapsedMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void capsRequestedRowsAtConfiguredMaximum() {
        QueryResult result = queryService.execute("SELECT id, name FROM customers ORDER BY id", 10_000);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void rejectsReadOnlyViolationBeforeJdbcExecution() {
        assertThatThrownBy(() -> queryService.execute("DELETE FROM customers", 10))
                .isInstanceOf(QueryRejectedException.class);
    }

    @Test
    void appliesResultByteLimitAndMarksTruncation() {
        QueryResult result = queryService.execute("SELECT REPEAT('x', 2000) AS value", 1);

        assertThat(result.rows()).isEmpty();
        assertThat(result.truncated()).isTrue();
    }
}
