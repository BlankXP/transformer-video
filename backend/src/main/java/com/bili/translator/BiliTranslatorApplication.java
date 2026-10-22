package com.bili.translator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties
@EnableScheduling
public class BiliTranslatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BiliTranslatorApplication.class, args);
    }
}
