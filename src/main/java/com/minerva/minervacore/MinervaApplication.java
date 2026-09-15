package com.minerva.minervacore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication()
public class MinervaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinervaApplication.class, args);
    }

}
