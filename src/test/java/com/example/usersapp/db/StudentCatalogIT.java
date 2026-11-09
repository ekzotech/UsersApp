package com.example.usersapp.db;

import static com.example.usersapp.domain.TeacherFixtures.teacher;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.StudentRepository;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherRepository;
import com.example.usersapp.service.StudentService;
import com.example.usersapp.support.PostgresTestBase;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;

class StudentCatalogIT extends PostgresTestBase {

    private static final Sort BY_NAME = Sort.by("fullName");

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findsStudentsByNameOrEmailIgnoringCaseInNameOrder() {
        studentRepository.save(new Student("Вера Кузнецова", "vera@example.com", "ИС-11"));
        studentRepository.save(new Student("Анна Смирнова", "anna@example.com", "ИС-12"));
        studentRepository.save(new Student("Борис Анненков", "boris@example.com", "ИС-13"));

        assertThat(studentRepository
                .findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase("анн", "анн", BY_NAME))
                .extracting(Student::getFullName)
                .containsExactly("Анна Смирнова", "Борис Анненков");

        assertThat(studentRepository
                .findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase("VERA", "VERA", BY_NAME))
                .extracting(Student::getEmail)
                .containsExactly("vera@example.com");
    }

    @Test
    void existsByEmailIgnoreCase() {
        studentRepository.save(new Student("Анна Смирнова", "s1@example.com", "ИС-11"));

        assertThat(studentRepository.existsByEmailIgnoreCase("S1@EXAMPLE.COM")).isTrue();
        assertThat(studentRepository.existsByEmailIgnoreCase("s2@example.com")).isFalse();
    }

    @Test
    void findsStudentsOfCuratorInNameOrder() {
        Teacher curator = teacher("Пётр Кураторов", "curator@example.com");
        curator.setCurator(true);
        teacherRepository.saveAndFlush(curator);
        Teacher other = teacherRepository.saveAndFlush(teacher("Ирина Другая", "other@example.com"));

        Student second = new Student("Борис Волков", "boris@example.com", "ИС-11");
        second.setCurator(curator);
        studentRepository.save(second);
        Student first = new Student("Алла Дроздова", "alla@example.com", "ИС-12");
        first.setCurator(curator);
        studentRepository.save(first);
        Student foreign = new Student("Глеб Иной", "gleb@example.com", "ИС-13");
        foreign.setCurator(other);
        studentRepository.save(foreign);
        studentRepository.save(new Student("Дина Безкураторова", "dina@example.com", "ИС-14"));
        studentRepository.flush();

        List<Student> ofCurator = studentRepository.findByCuratorIdOrderByFullName(curator.getId());

        assertThat(ofCurator).extracting(Student::getFullName).containsExactly("Алла Дроздова", "Борис Волков");
        assertThat(ofCurator).allSatisfy(student ->
                assertThat(student.getCurator().getId()).isEqualTo(curator.getId()));
    }

    @Test
    void deactivatedStudentIsShownAsExpelled() {
        Student saved = studentRepository.saveAndFlush(new Student("Анна Смирнова", "anna@example.com", "ИС-11"));

        Student active = reRead(saved.getId());
        // created_at is read-only over the DB default now(); the initial select skips the column,
        // so a fresh EntityManager is needed for it to be materialised.
        assertThat(active.getCreatedAt()).isNotNull();
        assertThat(active.isActive()).isTrue();
        assertThat(active.getDisplayStatus()).isEqualTo("Активен");

        active.setActive(false);
        studentRepository.saveAndFlush(active);

        Student expelled = reRead(saved.getId());
        assertThat(expelled.getDisplayStatus()).isEqualTo("Отчислен");
    }

    private Student reRead(Long id) {
        entityManager.clear();
        return studentRepository.findById(id).orElseThrow();
    }

    @Test
    void listingInitialisesCuratorForRendering() {
        Teacher curator = teacherRepository.save(teacher("Марина Курилова", "curator-list@example.com"));
        Student curated = studentRepository.save(new Student("Вера Орлова", "vera@example.com", "ИС-14"));
        curated.setCurator(curator);
        studentRepository.saveAndFlush(curated);
        entityManager.clear();

        List<Student> listed = new StudentService(studentRepository).findAll(null);

        // The list page renders the curator's name outside the transaction (open-in-view=false).
        assertThat(listed).extracting(s -> s.getCurator() == null ? null : s.getCurator().getFullName())
                .containsExactly("Марина Курилова");
        assertThat(Hibernate.isInitialized(listed.get(0).getCurator())).isTrue();
    }
}
