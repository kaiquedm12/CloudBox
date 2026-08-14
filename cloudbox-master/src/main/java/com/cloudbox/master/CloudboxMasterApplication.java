package com.cloudbox.master;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CloudboxMasterApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudboxMasterApplication.class, args);
    }
}