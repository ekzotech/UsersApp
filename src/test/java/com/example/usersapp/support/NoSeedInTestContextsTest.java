package com.example.usersapp.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.usersapp.UsersAppApplication;
import com.example.usersapp.domain.CourseRepository;
import com.example.usersapp.domain.StudentRepository;
import com.example.usersapp.domain.UserRepository;
import com.example.usersapp.seed.SeedRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The "seed" profile is the default profile (application.properties), so a full context runs the seed on
 * startup. Every full-context test other than SeedIntegrationIT switches the seed off with
 * {@code app.seed.enabled=false}; this test is that same start-up path, so it fails as soon as the switch
 * stops working and unrelated tests would start seeing (and colliding with) demo data.
 */
@SpringBootTest(classes = UsersAppApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
@TestPropertySource(properties = "app.seed.enabled=false")
class NoSeedInTestContextsTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Test
    void startsTheSeedAndWritesNoDemoData() {
        // The runner is part of this context (the default "seed" profile is active)...
        assertThat(applicationContext.getBeansOfType(SeedRunner.class)).hasSize(1);
        // ...it ran at startup with the switch off, so no demo data is there, while Flyway still ran.
        assertThat(userRepository.count()).isZero();
        assertThat(studentRepository.count()).isZero();
        assertThat(courseRepository.count()).isEqualTo(12);
    }
}
