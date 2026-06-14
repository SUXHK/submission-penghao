package com.defecttriage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DefectTriageApplication {
    public static void main(String[] args) {
        SpringApplication.run(DefectTriageApplication.class, args);
    }
}
