package com.example.mysqlmcpserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@ConfigurationPropertiesScan
@SpringBootApplication
public class MysqlMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MysqlMcpServerApplication.class, args);
    }

}
