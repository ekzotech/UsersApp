package com.example.usersapp.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.usersapp.UsersAppApplication;
import com.example.usersapp.domain.CourseRepository;
import com.example.usersapp.domain.EnrollmentRepository;
import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.StudentRepository;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherRepository;
import com.example.usersapp.domain.User;
import com.example.usersapp.domain.UserRepository;
import com.example.usersapp.domain.UserRole;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fills the database while the application starts: the default profile is "seed", the profile that
 * carries {@link SeedRunner}.
 */
@SpringBootTest(classes = UsersAppApplication.class)
@Testcontainers
@ActiveProfiles("seed") // the default profile of application.properties: pinned, so the test still means it
// Its own static @Container: repeat the AFTER_CLASS option (see IMPLEMENTATION.md).
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"app.seed.students=15", "app.seed.teachers=3", "app.seed.curators=2"})
class SeedIntegrationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void seedsOnStartup() {
        assertThat(studentRepository.count()).isEqualTo(15);
        assertThat(teacherRepository.count()).isEqualTo(3);
        assertThat(userRepository.count()).isEqualTo(2); // the admin and the account of the first curator
        assertThat(courseRepository.count()).isEqualTo(12);
        assertThat(enrollmentRepository.count()).isGreaterThan(0);
    }

    @Test
    void everyStudentHasAtMostOneCuratorAndItIsACurator() {
        List<Student> students = studentRepository.findAll();
        assertThat(students).hasSize(15);

        List<Long> curatorIds = students.stream()
                .map(Student::getCurator)
                .filter(Objects::nonNull)
                .map(Teacher::getId)
                .distinct()
                .toList();
        assertThat(curatorIds).isNotEmpty();
        // A student row carries a single curator_id, so "at most one" is structural; whether that teacher
        // really has the curator role is read back from the database.
        assertThat(teacherRepository.findAllById(curatorIds))
                .isNotEmpty()
                .allSatisfy(curator -> {
                    assertThat(curator.isCurator()).isTrue();
                    assertThat(curator.isActive()).isTrue();
                });
    }

    @Test
    void seedIsIdempotent() {
        Counts before = counts();

        // Looked up through the context (the rule for programmatic calls): a fresh instance would not
        // exercise the bean Spring manages.
        applicationContext.getBean(SeedRunner.class).run(null);

        assertThat(counts()).isEqualTo(before);
        assertThat(userRepository.findAll()).extracting(User::getRole)
                .containsExactlyInAnyOrder(UserRole.ADMIN, UserRole.TEACHER);
    }

    /**
     * The rollout switch of docker compose: with the seed disabled the application starts and writes no
     * demo data (Flyway still creates the schema).
     *
     * <p>This class runs its own container, and a second application context started from the test with its
     * own JDBC properties does not get them: the {@code @ServiceConnection} of this class outranks them and
     * would point the nested start at this class's already seeded database and at wrong credentials. The
     * switched-off start therefore gets a scratch container of its own, and passes the seed sizes of this
     * class explicitly: in-process starts from a test run see the test class's own properties.
     */
    @Test
    void startsWithoutSeedingWhenTheSeedIsDisabled() {
        PostgreSQLContainer<?> empty = new PostgreSQLContainer<>("postgres:17-alpine").withStartupAttempts(3);
        empty.start();
        try (empty;
             ConfigurableApplicationContext context = new SpringApplicationBuilder(UsersAppApplication.class)
                     .web(WebApplicationType.NONE) // a second context must not bind the already used port
                     .run("--spring.datasource.url=" + empty.getJdbcUrl(),
                             "--spring.datasource.username=" + empty.getUsername(),
                             "--spring.datasource.password=" + empty.getPassword(),
                             "--app.seed.enabled=false",
                             "--app.seed.students=15",
                             "--app.seed.teachers=3",
                             "--app.seed.curators=2")) {
            assertThat(context.getBean(UserRepository.class).count()).as("no demo accounts").isZero();
            assertThat(context.getBean(StudentRepository.class).count()).isZero();
            assertThat(context.getBean(CourseRepository.class).count()).as("Flyway still runs").isEqualTo(12);
        }
    }

    private Counts counts() {
        return new Counts(studentRepository.count(), teacherRepository.count(), userRepository.count(),
                enrollmentRepository.count());
    }

    private record Counts(long students, long teachers, long users, long enrollments) {
    }
}
