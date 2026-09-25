package dev.agentstudio.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RuntimeServiceApplication {
    public static void main(String[] args) { SpringApplication.run(RuntimeServiceApplication.class, args); }
}
