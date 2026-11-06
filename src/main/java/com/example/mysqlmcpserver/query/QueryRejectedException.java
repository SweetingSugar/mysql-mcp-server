package com.example.mysqlmcpserver.query;

public final class QueryRejectedException extends RuntimeException {

    public QueryRejectedException() {
        super("Only a single read-only query is allowed.");
    }
}
