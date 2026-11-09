package com.example.usersapp.seed;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the demo data seed (profile "seed"). Registered by the application through
 * {@code @ConfigurationPropertiesScan}; every value can be overridden with an {@code app.seed.*}
 * property (see application.properties).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.seed")
public class SeedProperties {

    private boolean enabled = true;

    private long randomSeed = 42;

    private int students = 300;

    private int teachers = 12;

    private int curators = 6;

    private String adminUsername = "admin";

    private String adminPassword = "admin12345";

    private String curatorUsername = "curator1";

    private String curatorPassword = "password";
}
