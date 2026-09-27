package com.memme;

import com.memme.config.QaAccountProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(QaAccountProperties.class)
public class MemmeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemmeApplication.class, args);
    }
}
