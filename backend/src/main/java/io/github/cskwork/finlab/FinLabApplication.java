package io.github.cskwork.finlab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinLabApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinLabApplication.class, args);
    }
}
