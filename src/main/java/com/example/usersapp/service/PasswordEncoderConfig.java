package com.example.usersapp.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    /**
     * Spring Security's delegating encoder: it encodes with BCrypt and stores the hash as
     * "{bcrypt}$2a$...". {@code matches()} only understands prefixed hashes - a bare "$2a$..." value
     * throws IllegalArgumentException - so every stored hash must be produced by {@code encode()}.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
