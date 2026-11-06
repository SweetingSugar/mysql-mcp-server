package com.example.mysqlmcpserver.query;

public final class DatabaseQueryException extends RuntimeException {

    public DatabaseQueryException(Throwable cause) {
        super("Database query failed.", cause);
    }
}
