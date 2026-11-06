package com.example.mysqlmcpserver.query;

public final class DatabaseResourceNotFoundException extends RuntimeException {

    public DatabaseResourceNotFoundException() {
        super("Table or view was not found.");
    }
}
