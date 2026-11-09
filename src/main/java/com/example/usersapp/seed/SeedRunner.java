package com.example.usersapp.seed;

import com.example.usersapp.domain.Course;
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
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.datafaker.Faker;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Fills an empty database with demo data when the application starts with the "seed" profile.
 * Everything comes from a Faker seeded with {@code app.seed.random-seed}, so two runs over an empty
 * database produce the same names. The presence of any user account means the seed already ran, so a
 * second start does nothing.
 */
@Component
@Profile("seed")
public class SeedRunner implements ApplicationRunner {

    // Guard below EnrollmentService.MAX_STUDENTS_PER_COURSE_PER_TEACHER (60): a seeded teacher teaches a
    // single course, and the even split keeps every (teacher, course) pair well under this cap anyway.
    private static final int MAX_SEEDED_STUDENTS_PER_COURSE = 30;

    private static final Logger LOG = System.getLogger(SeedRunner.class.getName());

    private final SeedProperties properties;
    private final Faker faker;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedRunner(SeedProperties properties,
                      Faker faker,
                      StudentRepository studentRepository,
                      TeacherRepository teacherRepository,
                      CourseRepository courseRepository,
                      EnrollmentRepository enrollmentRepository,
                      UserRepository userRepository,
                      PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.faker = faker;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        if (userRepository.count() > 0) {
            return;
        }
        List<Course> courses = courseRepository.findAll(Sort.by("id"));
        if (courses.isEmpty()) {
            LOG.log(Level.WARNING, "Seed skipped: no courses found (run the Flyway migrations first)");
            return;
        }

        List<Teacher> teachers = seedTeachers(courses);
        List<Student> students = seedStudents(teachers);
        int enrollments = seedEnrollments(students, teachers, courses);
        seedAccounts(teachers);

        LOG.log(Level.INFO, "Seed done: {0} teachers, {1} students, {2} enrollments",
                teachers.size(), students.size(), enrollments);
    }

    private List<Teacher> seedTeachers(List<Course> courses) {
        List<Teacher> teachers = new ArrayList<>();
        for (int i = 0; i < properties.getTeachers(); i++) {
            Teacher teacher = new Teacher(faker.name().fullName(), "seed-teacher-" + i + "@example.com",
                    i < properties.getCurators());
            teacher.getCourses().add(courses.get(i % courses.size()));
            teachers.add(teacher);
        }
        teacherRepository.saveAll(teachers);
        return teachers;
    }

    private List<Student> seedStudents(List<Teacher> teachers) {
        List<Teacher> curators = teachers.stream().filter(Teacher::isCurator).toList();
        List<Student> students = new ArrayList<>();
        for (int i = 0; i < properties.getStudents(); i++) {
            Student student = new Student(faker.name().fullName(), "seed-student-" + i + "@example.com",
                    "ИС-" + faker.number().numberBetween(11, 26));
            if (!curators.isEmpty() && i % 7 != 0) {
                student.setCurator(curators.get(i % curators.size()));
            }
            students.add(student);
        }
        studentRepository.saveAll(students);
        return students;
    }

    private int seedEnrollments(List<Student> students, List<Teacher> teachers, List<Course> courses) {
        // One seeded teacher per course model, so counting per course counts per (teacher, course).
        Map<Course, Integer> load = new HashMap<>();
        int created = 0;
        for (int i = 0; i < students.size(); i++) {
            if (i % 2 != 0) {
                continue;
            }
            Student student = students.get(i);
            List<Enrollment> pending = new ArrayList<>();
            for (int k = 0; k < 2; k++) {
                Course course = courses.get((i + k) % courses.size());
                // Same instances as the ones attached to the seeded teachers, so competency is a plain match.
                Teacher teacher = teachers.stream()
                        .filter(candidate -> candidate.getCourses().contains(course))
                        .findFirst()
                        .orElse(null);
                if (teacher == null) {
                    continue;
                }
                if (load.getOrDefault(course, 0) >= MAX_SEEDED_STUDENTS_PER_COURSE) {
                    continue;
                }
                load.merge(course, 1, Integer::sum);
                pending.add(new Enrollment(teacher, student, course));
            }
            pending.forEach(enrollmentRepository::save);
            created += pending.size();
        }
        return created;
    }

    private void seedAccounts(List<Teacher> teachers) {
        List<User> users = new ArrayList<>();
        teachers.stream()
                .filter(Teacher::isCurator)
                .findFirst()
                .ifPresent(curator -> {
                    User user = new User(properties.getCuratorUsername(),
                            passwordEncoder.encode(properties.getCuratorPassword()), UserRole.TEACHER);
                    user.setTeacher(curator);
                    users.add(user);
                });
        users.add(new User(properties.getAdminUsername(),
                passwordEncoder.encode(properties.getAdminPassword()), UserRole.ADMIN));
        userRepository.saveAll(users);
    }
}
