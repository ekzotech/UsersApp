package com.example.usersapp.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.usersapp.service.AppConfig;
import net.datafaker.Faker;
import org.junit.jupiter.api.Test;

class SeedPropertiesTest {

    @Test
    void carriesTheDocumentedDefaults() {
        SeedProperties properties = new SeedProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getRandomSeed()).isEqualTo(42L);
        assertThat(properties.getStudents()).isEqualTo(300);
        assertThat(properties.getTeachers()).isEqualTo(12);
        assertThat(properties.getCurators()).isEqualTo(6);
        assertThat(properties.getAdminUsername()).isEqualTo("admin");
        assertThat(properties.getAdminPassword()).isEqualTo("admin12345");
        assertThat(properties.getCuratorUsername()).isEqualTo("curator1");
        assertThat(properties.getCuratorPassword()).isEqualTo("password");
    }

    @Test
    void fakerBeanIsDeterministicForTheSameSeed() {
        SeedProperties first = new SeedProperties();
        SeedProperties second = new SeedProperties();
        second.setRandomSeed(first.getRandomSeed());

        Faker firstFaker = faker(first);
        Faker secondFaker = faker(second);

        for (int i = 0; i < 5; i++) {
            assertThat(firstFaker.name().fullName()).isEqualTo(secondFaker.name().fullName());
        }
    }

    /** The Faker bean exactly as {@code service.AppConfig} builds it, named locally for readability. */
    private static Faker faker(SeedProperties properties) {
        return new AppConfig().faker(properties);
    }
}
