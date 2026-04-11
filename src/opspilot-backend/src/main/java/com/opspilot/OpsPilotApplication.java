package com.opspilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.opspilot.mapper")
@EnableScheduling
public class OpsPilotApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpsPilotApplication.class, args);
    }
}
