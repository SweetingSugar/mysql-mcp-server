package com.example.mysqlmcpserver.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public final class ReadOnlySqlValidator {

    private static final Set<String> ALLOWED_FIRST_KEYWORDS = Set.of(
            "SELECT", "SHOW", "DESCRIBE", "DESC", "EXPLAIN");

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "MERGE", "REPLACE", "UPSERT",
            "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME", "GRANT", "REVOKE",
            "CALL", "SET", "START", "COMMIT", "ROLLBACK", "SAVEPOINT", "RELEASE",
            "LOCK", "INTO", "PROCEDURE", "HANDLER", "LOAD", "DO", "LOAD_FILE",
            "SLEEP", "BENCHMARK");

    public String validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new QueryRejectedException();
        }

        String firstKeyword = firstKeyword(sql);
        if (!ALLOWED_FIRST_KEYWORDS.contains(firstKeyword)) {
            throw new QueryRejectedException();
        }

        for (String token : tokenize(sql)) {
            if (FORBIDDEN_KEYWORDS.contains(token)) {
                throw new QueryRejectedException();
            }
        }
        return sql.trim();
    }

    private String firstKeyword(String sql) {
        int index = skipWhitespace(sql, 0);
        if (index >= sql.length() || !isIdentifierStart(sql.charAt(index))) {
            throw new QueryRejectedException();
        }
        int end = consumeIdentifier(sql, index);
        return sql.substring(index, end).toUpperCase(Locale.ROOT);
    }

    private List<String> tokenize(String sql) {
        List<String> tokens = new ArrayList<>();
        int index = 0;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                index = skipQuoted(sql, index, current);
                continue;
            }
            if (current == ';' || current == '#'
                    || (current == '-' && hasNext(sql, index) && sql.charAt(index + 1) == '-')
                    || (current == '/' && hasNext(sql, index) && sql.charAt(index + 1) == '*')
                    || (current == '*' && hasNext(sql, index) && sql.charAt(index + 1) == '/')) {
                throw new QueryRejectedException();
            }
            if (isIdentifierStart(current)) {
                int end = consumeIdentifier(sql, index);
                tokens.add(sql.substring(index, end).toUpperCase(Locale.ROOT));
                index = end;
                continue;
            }
            index++;
        }
        return tokens;
    }

    private int skipQuoted(String sql, int start, char quote) {
        int index = start + 1;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (current == '\\' && index + 1 < sql.length()) {
                index += 2;
                continue;
            }
            if (current == quote) {
                if (index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                    index += 2;
                    continue;
                }
                return index + 1;
            }
            index++;
        }
        throw new QueryRejectedException();
    }

    private int skipWhitespace(String sql, int start) {
        int index = start;
        while (index < sql.length() && Character.isWhitespace(sql.charAt(index))) {
            index++;
        }
        return index;
    }

    private int consumeIdentifier(String sql, int start) {
        int index = start + 1;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (!Character.isLetterOrDigit(current) && current != '_' && current != '$') {
                break;
            }
            index++;
        }
        return index;
    }

    private boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_' || value == '$';
    }

    private boolean hasNext(String value, int index) {
        return index + 1 < value.length();
    }
}
