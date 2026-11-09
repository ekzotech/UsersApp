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
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import net.datafaker.Faker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Mockito's own extension is not used: its "unnecessary stubbing" check silently reported unused stubs
 * without test code and was unreliable here, and the tests stub exactly what each scenario needs. The mocks
 * are opened per test through {@link MockitoAnnotations}.
 */
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

    private AutoCloseable mocks;

    private SeedProperties properties;

    /**
     * Mocks are created by hand because the stubs are shared through helpers: {@code MockitoExtension}'s
     * strict stubbing (the default) would be replaced by a {@code MockitoSession} with {@code STRICT_STUBS}
     * if argument-matching lint is wanted here.
     */
    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        properties = new SeedProperties();
    }

    @AfterEach
    void closeMocks() throws Exception {
        mocks.close();
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
        Mockito.when(userRepository.count()).thenReturn(1L);

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
        givenEmptyDatabaseWithTheCatalogue();

        runner(faker()).run(null);

        assertThat(savedTeachers()).hasSize(3);
        assertThat(savedStudents()).hasSize(10);
    }

    @Test
    void firstCuratorsAreMarkedAndTeachAtLeastOneCourse() {
        properties.setTeachers(3);
        properties.setCurators(2);
        properties.setStudents(10);
        givenEmptyDatabaseWithTheCatalogue();

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
        givenEmptyDatabaseWithTheCatalogue();

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
        givenEmptyDatabaseWithTheCatalogue();

        runner(faker()).run(null);

        List<Enrollment> enrollments = savedEnrollments();
        assertThat(enrollments).isNotEmpty();
        assertThat(enrollments).allSatisfy(enrollment -> {
            assertThat(enrollment.getTeacher().teaches(enrollment.getCourse())).isTrue();
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
        givenEmptyDatabaseWithTheCatalogue();

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
        givenEmptyDatabaseWithTheCatalogue();
        runner(faker()).run(null);
        List<Student> firstRun = savedStudents();

        givenEmptyDatabaseWithTheCatalogue();
        clearInvocations(studentRepository);
        runner(faker()).run(null);
        List<Student> secondRun = savedStudents();

        assertThat(secondRun).extracting(Student::getFullName)
                .containsExactlyElementsOf(firstRun.stream().map(Student::getFullName).toList());
    }

    @Test
    void createsEnrollmentsWithinPerTeacherCourseLimit() {
        // Two teachers per course: each course shares two teachers, and each (teacher, course) pair gets
        // half of that course's load.
        properties.setTeachers(24);
        properties.setCurators(6);
        properties.setStudents(400);
        givenEmptyDatabaseWithTheCatalogue();

        runner(faker()).run(null);

        Map<Course, Long> enrolledPerCourse = savedEnrollments().stream()
                .collect(Collectors.groupingBy(Enrollment::getCourse, Collectors.counting()));
        Map<String, Long> savedPerPair = savedEnrollments().stream()
                .collect(Collectors.groupingBy(
                        enrollment -> enrollment.getTeacher().getEmail() + " " + enrollment.getCourse().getId(),
                        Collectors.counting()));
        assertThat(enrolledPerCourse).as("every course gets students").hasSize(12);
        assertThat(savedPerPair).isNotEmpty()
                .allSatisfy((pair, saved) -> assertThat(saved)
                        .as("students of one teacher on one course (%s)", pair)
                        .isPositive()
                        .isLessThanOrEqualTo((long) SeedRunner.MAX_SEEDED_STUDENTS_PER_COURSE));
    }

    @Test
    void enrolsEveryCourseAStudentMeets() {
        // Twelve students walk the catalogue in pairs (0 meets 1+2, 2 meets 3+4, ... 10 meets 11+12), so the
        // whole catalogue is met; the last course is only reachable through the free teacher of that course.
        properties.setTeachers(12);
        properties.setCurators(2);
        properties.setStudents(12);
        List<Course> courses = catalogue(12);
        Mockito.when(courseRepository.findAll(any(Sort.class))).thenReturn(courses);
        Mockito.when(userRepository.count()).thenReturn(0L);
        Mockito.when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));

        runner(faker()).run(null);

        assertThat(savedEnrollments()).extracting(Enrollment::getCourse).containsAll(courses);
        assertThat(savedEnrollments()).isNotEmpty().allSatisfy(enrollment ->
                assertThat(enrollment.getTeacher().teaches(enrollment.getCourse())).isTrue());
    }

    @Test
    void neverEnrolsTheSameStudentTwiceInACourseWithDifferentTeachers() {
        // An odd catalogue makes the second slot of some students repeat their first course; a student with
        // exactly two places must get a different course there instead (no course twice, two per student).
        properties.setTeachers(4);
        properties.setCurators(1);
        properties.setStudents(20);
        Mockito.when(courseRepository.findAll(any(Sort.class))).thenReturn(catalogue(13));
        Mockito.when(userRepository.count()).thenReturn(0L);
        Mockito.when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));

        runner(faker()).run(null);

        List<Enrollment> enrollments = savedEnrollments();
        assertThat(enrollments).isNotEmpty();
        assertThat(enrollments)
                .extracting(enrollment -> enrollment.getStudent().getEmail() + " " + enrollment.getCourse().getId())
                .as("a student gets one teacher per course at most")
                .doesNotHaveDuplicates();
        assertThat(enrollments.stream().map(enrollment -> enrollment.getStudent().getEmail()).distinct().toList())
                .as("every second student is still enrolled").hasSize(properties.getStudents() / 2);
        assertThat(enrollments).as("two courses per enrolled student").hasSize(properties.getStudents() / 2 * 2);
    }

    @Test
    void enrolsAsManyCoursesAsTheTeacherPatternAllows() {
        // Three teachers over the 12 catalogue courses, two courses each: one teacher can be at the
        // per-(teacher, course) limit of 30, the other two keep room for all ten places each student asks
        // for, so all 10 of the even students get their two courses.
        properties.setTeachers(3);
        properties.setCurators(1);
        properties.setStudents(20);
        givenEmptyDatabaseWithTheCatalogue();

        runner(faker()).run(null);

        List<Enrollment> enrollments = savedEnrollments();
        assertThat(enrollments).isNotEmpty();
        assertThat(enrollments).hasSize(properties.getStudents() / 2 * 2);
        assertThat(enrollments)
                .extracting(enrollment -> enrollment.getStudent().getEmail() + " " + enrollment.getCourse().getId())
                .doesNotHaveDuplicates();
    }

    @Test
    void enrolledCountMatchesTheEnrollmentsSaved() {
        properties.setTeachers(12);
        properties.setCurators(2);
        properties.setStudents(20);
        givenEmptyDatabaseWithTheCatalogue();

        runner(faker()).run(null);

        List<Enrollment> enrollments = savedEnrollments();
        assertThat(enrollments).isNotEmpty();
        // Every second student is enrolled, up to two courses each; at this size the limit drops nothing.
        assertThat(enrollments).hasSizeLessThanOrEqualTo(properties.getStudents() / 2 * 2);
        assertThat(enrollments).hasSize(properties.getStudents() / 2 * 2);
    }

    /** An empty users table and the 12 catalogue courses as the migrations insert them (ids 1..12). */
    private void givenEmptyDatabaseWithTheCatalogue() {
        Mockito.when(userRepository.count()).thenReturn(0L);
        Mockito.when(courseRepository.findAll(any(Sort.class))).thenReturn(catalogue(12));
        Mockito.when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
    }

    private static List<Course> catalogue(int size) {
        List<Course> courses = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            Course course = CourseFixtures.course("course-" + i, "Предмет " + i);
            // The migrations insert ids 1..n, and the seed handles the catalogue in that order.
            ReflectionTestUtils.setField(course, "id", (long) i);
            courses.add(course);
        }
        return courses;
    }

    private SeedRunner runner(Faker faker) {
        // run() seeds through the bean's own transactional proxy; outside Spring that is the instance itself.
        SeedRunner runner = new SeedRunner(properties, faker, studentRepository, teacherRepository,
                courseRepository, enrollmentRepository, userRepository, passwordEncoder, null);
        ReflectionTestUtils.setField(runner, "self", runner);
        return runner;
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
