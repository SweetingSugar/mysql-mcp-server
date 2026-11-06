package com.example.mysqlmcpserver.query;

public final class DatabaseQueryTimeoutException extends RuntimeException {

    public DatabaseQueryTimeoutException(Throwable cause) {
        super("Query timed out.", cause);
    }
}
