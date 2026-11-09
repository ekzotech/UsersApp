package com.example.usersapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class UsersAppApplicationTest {

    @Test
    void appClassIsAnnotatedWithSpringBootApplication() {
        assertThat(UsersAppApplication.class.isAnnotationPresent(SpringBootApplication.class)).isTrue();
    }
}
