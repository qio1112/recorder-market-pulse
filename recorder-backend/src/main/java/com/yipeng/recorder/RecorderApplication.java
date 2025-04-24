package com.yipeng.recorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RecorderApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecorderApplication.class, args);
    }

}
