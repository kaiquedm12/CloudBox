package com.cloudbox.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CloudboxAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudboxAgentApplication.class, args);
    }
}
