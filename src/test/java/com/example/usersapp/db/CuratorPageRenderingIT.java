package com.example.usersapp.db;

import static com.example.usersapp.domain.TeacherFixtures.teacher;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.StudentRepository;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherRepository;
import com.example.usersapp.domain.User;
import com.example.usersapp.domain.UserRepository;
import com.example.usersapp.domain.UserRole;
import com.example.usersapp.service.CuratorService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.hibernate.Hibernate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

/**
 * Full-context call of {@link CuratorService#studentsOfCurator}: the service runs in its own committed
 * transaction, so the returned rows are what the page receives (open-in-view is disabled). It checks the
 * rendered data is readable; the discriminating guard for the in-transaction touch lives in
 * {@code CuratorRepositoryIT.studentsOfCuratorInitialisesCuratorsForRendering} (a test session can hand
 * back already-initialised proxies, so an {@code isInitialized} assertion alone proves little).
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CuratorPageRenderingIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private CuratorService curatorService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearDatabase() {
        studentRepository.deleteAll();
        userRepository.deleteAll();
        teacherRepository.deleteAll();
    }

    @Test
    void studentsOfCuratorCarryCuratorNamesForRendering() {
        Teacher curator = teacherRepository.save(teacher("Марина Курилова", "curator@example.com"));
        curator.setCurator(true);
        teacherRepository.save(curator);
        User account = userRepository.save(new User("curator1", "{bcrypt}x", UserRole.TEACHER));
        account.setTeacher(curator);
        userRepository.saveAndFlush(account);

        Student first = studentRepository.save(new Student("Вера Орлова", "vera@example.com", "ИС-14"));
        first.setCurator(curator);
        Student second = studentRepository.save(new Student("Анна Смирнова", "anna@example.com", "ИС-11"));
        second.setCurator(curator);
        studentRepository.saveAndFlush(first);
        studentRepository.saveAndFlush(second);

        List<Student> students = curatorService.studentsOfCurator("curator1");

        assertThat(students).hasSize(2);
        // Humane detach: every remaining entity except the curator proxies is detached by an update.
        userRepository.findAll().forEach(user -> user.setPassword(user.getPassword() + " "));
        userRepository.flush();
        teacherRepository.findAll().forEach(t -> t.setEmail(t.getEmail() + " "));
        teacherRepository.flush();
        students.forEach(student -> {
            assertThat(Hibernate.isInitialized(student.getCurator()))
                    .as("template renders s.curator.fullName outside the service transaction")
                    .isTrue();
            assertThat(student.getCurator().getFullName()).isEqualTo("Марина Курилова");
        });
    }
}
