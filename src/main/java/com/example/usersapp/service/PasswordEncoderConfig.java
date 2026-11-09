package com.example.usersapp.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    /**
     * Spring Security's delegating encoder: it encodes with BCrypt and keeps the "{bcrypt}" prefix in
     * the stored hash, so a plain BCrypt hash and a prefixed one both verify against stored data.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
