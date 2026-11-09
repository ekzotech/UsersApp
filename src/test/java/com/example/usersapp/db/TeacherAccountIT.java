package com.example.usersapp.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherRepository;
import com.example.usersapp.domain.User;
import com.example.usersapp.domain.UserRepository;
import com.example.usersapp.domain.UserRole;
import com.example.usersapp.service.DuplicateEmailException;
import com.example.usersapp.service.DuplicateUsernameException;
import com.example.usersapp.service.PasswordEncoderConfig;
import com.example.usersapp.service.TeacherService;
import com.example.usersapp.support.PostgresTestBase;
import com.example.usersapp.web.TeacherForm;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * DB-level checks of teacher creation that a mocked service cannot catch: the real schema
 * (unique indexes, NOT NULL columns, the account's foreign key) and the hash written for the
 * account, which must be verifiable by the login encoder.
 */
@Import({TeacherService.class, PasswordEncoderConfig.class})
class TeacherAccountIT extends PostgresTestBase {

    private static final String PASSWORD = "secret123";

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createPersistsTeacherAndAccountThatCanLogIn() {
        Teacher created = teacherService.create(form("Иван Иванов", "ivanov@example.com", "ivanov", true));

        entityManager.clear();

        Teacher teacher = teacherRepository.findById(created.getId()).orElseThrow();
        assertThat(teacher.getFullName()).isEqualTo("Иван Иванов");
        assertThat(teacher.getEmail()).isEqualTo("ivanov@example.com");
        assertThat(teacher.isCurator()).isTrue();
        assertThat(teacher.isActive()).isTrue();
        assertThat(teacher.getCreatedAt()).isNotNull();

        User account = userRepository.findByUsername("ivanov").orElseThrow();
        assertThat(account.getRole()).isEqualTo(UserRole.TEACHER);
        assertThat(account.getTeacher().getId()).isEqualTo(created.getId());
        assertThat(account.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, account.getPassword())).isTrue();
    }

    @Test
    void duplicateEmailInAnotherCaseIsRejected() {
        teacherService.create(form("Иван Иванов", "ivanov@example.com", "ivanov", false));

        assertThatThrownBy(() ->
                        teacherService.create(form("Пётр Петров", "IVANOV@example.com", "petrov", false)))
                .isInstanceOf(DuplicateEmailException.class);

        assertThat(teacherRepository.count()).isEqualTo(1);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void duplicateUsernameInAnotherCaseIsRejected() {
        teacherService.create(form("Иван Иванов", "ivanov@example.com", "ivanov", false));

        assertThatThrownBy(() ->
                        teacherService.create(form("Пётр Петров", "petrov@example.com", "IVANOV", false)))
                .isInstanceOf(DuplicateUsernameException.class);

        assertThat(teacherRepository.count()).isEqualTo(1);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void deactivateMarksTeacherInactiveInTheDatabase() {
        Teacher created = teacherService.create(form("Иван Иванов", "ivanov@example.com", "ivanov", false));

        Teacher deactivated = teacherService.deactivate(created.getId());
        teacherRepository.flush();

        assertThat(deactivated.getDisplayStatus()).isEqualTo("Уволен");
        // The fixture transaction also wraps the test, so the verified state lives in the row.
        Boolean active = jdbcTemplate.queryForObject(
                "SELECT active FROM teachers WHERE id = ?", Boolean.class, created.getId());
        assertThat(active).isFalse();
    }

    private static TeacherForm form(String fullName, String email, String username, boolean curator) {
        TeacherForm form = new TeacherForm();
        form.setFullName(fullName);
        form.setEmail(email);
        form.setUsername(username);
        form.setPassword(PASSWORD);
        form.setCurator(curator);
        return form;
    }
}
