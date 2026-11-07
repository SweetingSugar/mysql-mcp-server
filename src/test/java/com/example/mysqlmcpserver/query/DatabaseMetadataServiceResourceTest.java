package com.example.mysqlmcpserver.query;

import com.example.mysqlmcpserver.query.model.TableSummary;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseMetadataServiceResourceTest {

    @Test
    void loadsListTablesSqlFromClasspathResource() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<TableSummary>>any()))
                .thenReturn(List.of());

        DatabaseMetadataService service = new DatabaseMetadataService(
                jdbcTemplate,
                new ClassPathResource("sql/metadata-list-tables.sql"),
                new ClassPathResource("sql/metadata-describe-table.sql"));

        service.listTables();

        verify(jdbcTemplate).query(
                org.mockito.ArgumentMatchers.<String>argThat(sql -> sql.contains("FROM information_schema.tables")
                        && sql.contains("ORDER BY TABLE_NAME")),
                org.mockito.ArgumentMatchers.<RowMapper<TableSummary>>any());
    }

    @Test
    void failsDuringConstructionWhenSqlResourceCannotBeRead() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        assertThatThrownBy(() -> new DatabaseMetadataService(
                jdbcTemplate,
                new ClassPathResource("sql/missing-query.sql"),
                new ClassPathResource("sql/metadata-describe-table.sql")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to load SQL resource");
    }
}
