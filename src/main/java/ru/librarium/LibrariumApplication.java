package ru.librarium;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.librarium.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class LibrariumApplication {
    public static void main(String[] args) {
        SpringApplication.run(LibrariumApplication.class, args);
    }
}
