package com.minishop.project.minishop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MiniShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniShopApplication.class, args);
    }

}
