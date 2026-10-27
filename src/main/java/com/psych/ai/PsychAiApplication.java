package com.psych.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class PsychAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(PsychAiApplication.class, args);
    }
}
