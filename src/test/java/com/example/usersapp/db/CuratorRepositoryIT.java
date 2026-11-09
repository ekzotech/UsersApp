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
import com.example.usersapp.support.PostgresTestBase;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CuratorRepositoryIT extends PostgresTestBase {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;


    @BeforeEach
    void cleanTables() {
        studentRepository.deleteAll();
        userRepository.deleteAll();
        teacherRepository.deleteAll();
    }

    @Test
    void findsActiveCuratorsInNameOrder() {
        Teacher dismissed = teacher("Борис Уволенный", "boris-c@example.com");
        dismissed.setCurator(true);
        dismissed.setActive(false);
        teacherRepository.saveAll(List.of(
                curator("Яков Кураторов", "yakov@example.com"),
                curator("Анна Кураторова", "anna-c@example.com"),
                dismissed,
                teacher("Иван Предметников", "ivan@example.com")));
        teacherRepository.flush();

        assertThat(teacherRepository.findByCuratorTrueAndActiveTrueOrderByFullName())
                .extracting(Teacher::getFullName)
                .containsExactly("Анна Кураторова", "Яков Кураторов");
    }

    @Test
    void findsStudentsOfCuratorInNameOrder() {
        Teacher curator = curator("Пётр Кураторов", "curator-repo@example.com");
        teacherRepository.saveAndFlush(curator);
        studentRepository.saveAll(List.of(
                curated("Борис Волков", "boris-s@example.com", curator),
                curated("Алла Дроздова", "alla-s@example.com", curator),
                new Student("Дина Безкураторова", "dina-s@example.com", "ИС-14")));
        studentRepository.flush();

        assertThat(studentRepository.findByCuratorIdOrderByFullName(curator.getId()))
                .extracting(Student::getFullName)
                .containsExactly("Алла Дроздова", "Борис Волков");
    }

    @Test
    void studentsOfCuratorInitialisesCuratorsForRendering() {
        Teacher curator = curator("Пётр Кураторов", "curator-it@example.com");
        teacherRepository.saveAndFlush(curator);
        Student own = curated("Анна Смирнова", "anna-it@example.com", curator);
        studentRepository.saveAndFlush(own);
        Student unassigned = new Student("Дина Безкураторова", "dina-it@example.com", "ИС-14");
        studentRepository.saveAndFlush(unassigned);
        User account = new User("curator1", "encoded", UserRole.TEACHER);
        account.setTeacher(curator);
        userRepository.saveAndFlush(account);
        Long unassignedId = unassigned.getId();
        entityManager.clear();

        // open-in-view=false: rendering reads `s.curator.fullName` after the service transaction has
        // committed. Handing the service rows that are already detached reproduces that position, so a
        // missing in-transaction touch shows up here as a LazyInitializationException.
        StudentRepository detachedRows = detachedRows(unassignedId);
        List<Student> students = new CuratorService(detachedRows, teacherRepository, userRepository)
                .studentsOfCurator("curator1");

        assertThat(students).extracting(Student::getFullName).containsExactly("Анна Смирнова");
        assertThat(students).extracting(s -> s.getCurator().getFullName()).containsOnly("Пётр Кураторов");
        assertThat(Hibernate.isInitialized(students.get(0).getCurator()))
                .as("curator must be initialised before the view renders")
                .isTrue();
    }

    /** Answers like the real query (only the curated student) but returns the row detached. */
    private StudentRepository detachedRows(Long unassignedId) {
        return (StudentRepository) java.lang.reflect.Proxy.newProxyInstance(
                StudentRepository.class.getClassLoader(),
                new Class<?>[] {StudentRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByCuratorIdOrderByFullName")) {
                        Student row = (Student) entityManager
                                .createQuery("select s from Student s where s.id <> :id", Student.class)
                                .setParameter("id", unassignedId)
                                .getSingleResult();
                        entityManager.detach(row);
                        return List.of(row);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Teacher curator(String fullName, String email) {
        Teacher teacher = teacher(fullName, email);
        teacher.setCurator(true);
        return teacher;
    }

    private static Student curated(String fullName, String email, Teacher curator) {
        Student student = new Student(fullName, email, "ИС-11");
        student.setCurator(curator);
        return student;
    }
}
