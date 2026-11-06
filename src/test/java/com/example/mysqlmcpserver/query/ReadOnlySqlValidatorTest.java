package com.example.mysqlmcpserver.query;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadOnlySqlValidatorTest {

    private final ReadOnlySqlValidator validator = new ReadOnlySqlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT id, name FROM customers",
            "  select count(*) from customers",
            "SHOW TABLES",
            "DESCRIBE customers",
            "DESC customers",
            "EXPLAIN SELECT * FROM customers",
            "SELECT 'semicolon;inside string'"
    })
    void acceptsSupportedReadOnlyStatements(String sql) {
        assertThatCode(() -> validator.validate(sql)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "INSERT INTO customers VALUES (1, 'x')",
            "UPDATE customers SET name = 'x'",
            "DELETE FROM customers",
            "DROP TABLE customers",
            "ALTER TABLE customers ADD COLUMN x INT",
            "CALL refresh_data()",
            "SET sql_mode = 'x'",
            "SELECT * FROM customers; DELETE FROM customers",
            "SELECT * FROM customers /* hidden */",
            "-- comment\nSELECT * FROM customers",
            "SELECT * FROM customers # comment",
            "SELECT * FROM customers INTO OUTFILE '/tmp/out'",
            "SELECT * FROM customers FOR UPDATE",
            "SELECT * FROM customers LOCK IN SHARE MODE"
    })
    void rejectsStatementsOutsideReadOnlyBoundary(String sql) {
        assertThatThrownBy(() -> validator.validate(sql))
                .isInstanceOf(QueryRejectedException.class);
    }
}
