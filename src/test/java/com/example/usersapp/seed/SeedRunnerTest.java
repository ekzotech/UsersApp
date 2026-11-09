package com.example.usersapp.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.usersapp.domain.Course;
import com.example.usersapp.domain.CourseFixtures;
import com.example.usersapp.domain.CourseRepository;
import com.example.usersapp.domain.Enrollment;
import com.example.usersapp.domain.EnrollmentRepository;
import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.StudentRepository;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherRepository;
import com.example.usersapp.domain.User;
import com.example.usersapp.domain.UserRepository;
import com.example.usersapp.domain.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SeedRunnerTest {

        @Mock
    private StudentRepository studentRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private SeedProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SeedProperties();
    }

    @Test
    void doesNothingWhenDisabled() {
        properties.setEnabled(false);

        runner(faker()).run(null);

        verifyNoInteractions(courseRepository, teacherRepository, studentRepository,
                enrollmentRepository, passwordEncoder);
        verify(userRepository, never()).saveAll(anyList());
    }

    @Test
    void doesNothingWhenUsersExist() {
        when(userRepository.count()).thenReturn(1L);

        runner(faker()).run(null);

        verifyNoInteractions(courseRepository, teacherRepository, studentRepository,
                enrollmentRepository, passwordEncoder);
        verify(userRepository, never()).saveAll(anyList());
    }

    @Test
    void createsRequestedNumbers() {
        properties.setTeachers(3);
        properties.setCurators(2);
        properties.setStudents(10);
        givenEmptyDatabase();

        runner(faker()).run(null);

        assertThat(savedTeachers()).hasSize(3);
        assertThat(savedStudents()).hasSize(10);
    }

    @Test
    void firstCuratorsAreMarkedAndTeachAtLeastOneCourse() {
        properties.setTeachers(3);
        properties.setCurators(2);
        properties.setStudents(10);
        givenEmptyDatabase();

        runner(faker()).run(null);

        List<Teacher> teachers = savedTeachers();
        assertThat(teachers.get(0).isCurator()).isTrue();
        assertThat(teachers.get(1).isCurator()).isTrue();
        assertThat(teachers.get(2).isCurator()).isFalse();
        assertThat(teachers).allSatisfy(teacher -> assertThat(teacher.getCourses()).isNotEmpty());
        assertThat(teachers).allSatisfy(teacher -> assertThat(teacher.isActive()).isTrue());
        assertThat(teachers).allSatisfy(teacher -> assertThat(teacher.getFullName()).isNotBlank());
        assertThat(teachers).extracting(Teacher::getEmail).doesNotHaveDuplicates();
    }

    @Test
    void studentsReferenceOnlyCuratorTeachers() {
        properties.setTeachers(3);
        properties.setCurators(2);
        properties.setStudents(10);
        givenEmptyDatabase();

        runner(faker()).run(null);

        List<Teacher> curators = savedTeachers().stream().filter(Teacher::isCurator).toList();
        List<Student> students = savedStudents();
        assertThat(students).allSatisfy(student -> {
            if (student.getCurator() != null) {
                assertThat(curators).contains(student.getCurator());
            }
        });
        assertThat(students).extracting(Student::getCurator).anyMatch(Objects::isNull);
        assertThat(students).extracting(Student::getGroupName).allMatch(group -> group.matches("ИС-\\d{2}"));
        assertThat(students).extracting(Student::getEmail).doesNotHaveDuplicates();
    }

    @Test
    void everyEnrollmentTeacherTeachesItsCourse() {
        properties.setTeachers(12);
        properties.setCurators(6);
        properties.setStudents(20);
        givenEmptyDatabase();

        runner(faker()).run(null);

        List<Enrollment> enrollments = savedEnrollments();
        assertThat(enrollments).isNotEmpty();
        assertThat(enrollments).allSatisfy(enrollment -> {
            assertThat(enrollment.getTeacher().getCourses()).contains(enrollment.getCourse());
            assertThat(enrollment.getStudent()).isNotNull();
        });
        assertThat(enrollments)
                .extracting(enrollment -> enrollment.getStudent().getEmail() + " " + enrollment.getCourse().getSlug())
                .doesNotHaveDuplicates();
    }

    @Test
    void createsAdminAndCuratorAccounts() {
        properties.setTeachers(3);
        properties.setCurators(2);
        properties.setStudents(10);
        givenEmptyDatabase();

        runner(faker()).run(null);

        List<User> users = savedUsers();
        assertThat(users).hasSize(2);
        User admin = users.stream().filter(user -> user.getRole() == UserRole.ADMIN).findFirst().orElseThrow();
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getPassword()).isEqualTo("enc:admin12345");
        User curator = users.stream().filter(user -> user.getRole() == UserRole.TEACHER).findFirst().orElseThrow();
        assertThat(curator.getUsername()).isEqualTo("curator1");
        assertThat(curator.getPassword()).isEqualTo("enc:password");
        assertThat(curator.getTeacher()).isSameAs(savedTeachers().get(0));
        verify(passwordEncoder).encode("admin12345");
        verify(passwordEncoder).encode("password");
    }

    @Test
    void sameSeedProducesSameNames() {
        properties.setTeachers(3);
        properties.setCurators(2);
        properties.setStudents(10);
        givenEmptyDatabase();
        runner(faker()).run(null);
        List<Student> firstRun = savedStudents();

        givenEmptyDatabase();
        clearInvocations(studentRepository);
        runner(faker()).run(null);
        List<Student> secondRun = savedStudents();

        assertThat(secondRun).extracting(Student::getFullName)
                .containsExactlyElementsOf(firstRun.stream().map(Student::getFullName).toList());
    }

    private void givenEmptyDatabase() {
        when(userRepository.count()).thenReturn(0L);
        List<Course> courses = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            courses.add(CourseFixtures.course("course-" + i, "Предмет " + i));
        }
        when(courseRepository.findAll(any(Sort.class))).thenReturn(courses);
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
    }

    private SeedRunner runner(Faker faker) {
        return new SeedRunner(properties, faker, studentRepository, teacherRepository, courseRepository,
                enrollmentRepository, userRepository, passwordEncoder);
    }

    private Faker faker() {
        return new Faker(new Locale("ru"), new Random(properties.getRandomSeed()));
    }

    private List<Teacher> savedTeachers() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Teacher>> captor = ArgumentCaptor.forClass(List.class);
        verify(teacherRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private List<Student> savedStudents() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Student>> captor = ArgumentCaptor.forClass(List.class);
        verify(studentRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private List<Enrollment> savedEnrollments() {
        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private List<User> savedUsers() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<User>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRepository).saveAll(captor.capture());
        return captor.getValue();
    }
}
