package com.example.ecomserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EcomMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcomMcpServerApplication.class, args);
    }
}
