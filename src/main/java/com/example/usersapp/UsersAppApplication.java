package com.example.usersapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UsersAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(UsersAppApplication.class, args);
    }
}
