package com.example.wordtopdf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WordToPdfApplication {

    public static void main(String[] args) {
        SpringApplication.run(WordToPdfApplication.class, args);
    }
}
