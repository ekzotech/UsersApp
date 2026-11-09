package com.example.usersapp.service;

import com.example.usersapp.seed.SeedProperties;
import java.util.Locale;
import java.util.Random;
import net.datafaker.Faker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SeedProperties.class)
public class AppConfig {

    /**
     * Seeded Faker so that two seed runs produce the same names (datafaker is thread-safe here
     * because it is only used on the single startup thread).
     */
    @Bean
    public Faker faker(SeedProperties properties) {
        return new Faker(new Locale("ru"), new Random(properties.getRandomSeed()));
    }
}
