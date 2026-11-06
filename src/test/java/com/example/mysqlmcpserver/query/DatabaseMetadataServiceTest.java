package com.example.mysqlmcpserver.query;

import com.example.mysqlmcpserver.query.model.ColumnDescription;
import com.example.mysqlmcpserver.query.model.TableDescription;
import com.example.mysqlmcpserver.query.model.TableSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
        "app.mcp.auth-token=test-token",
        "app.query.max-rows=1000",
        "app.query.max-result-bytes=1048576",
        "app.query.timeout-seconds=10"
})
class DatabaseMetadataServiceTest {

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
    DatabaseMetadataService metadataService;

    @Test
    void listsTablesAndViews() {
        assertThat(metadataService.listTables().tables())
                .extracting(TableSummary::tableName)
                .contains("customers", "customer_names");
    }

    @Test
    void describesColumnsAndPrimaryKey() {
        TableDescription result = metadataService.describeTable("customers");

        assertThat(result.columns()).extracting(ColumnDescription::columnName)
                .containsExactly("id", "name", "created_at");
        assertThat(result.columns().get(0).primaryKeyPosition()).isEqualTo(1);
    }

    @Test
    void reportsMissingTableWithoutExecutingUserSql() {
        assertThatThrownBy(() -> metadataService.describeTable("missing_table"))
                .isInstanceOf(DatabaseResourceNotFoundException.class);
    }
}
