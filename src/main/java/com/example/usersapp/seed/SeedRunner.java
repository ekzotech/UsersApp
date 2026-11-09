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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.datafaker.Faker;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills an empty database with demo data when the application starts with the "seed" profile.
 * Everything comes from a Faker seeded with {@code app.seed.random-seed}, so two runs over an empty
 * database produce the same names. The presence of any user account means the seed already ran, so a
 * second start does nothing.
 *
 * <p>The writes happen in one transaction (see {@link #seed()}), so a failure halfway leaves an empty
 * database that the next start seeds again instead of a half-seeded one the user guard would keep.
 */
@Component
@Profile("seed")
public class SeedRunner implements ApplicationRunner {

    // Below EnrollmentService.MAX_STUDENTS_PER_COURSE_PER_TEACHER (60): the seeded data must never trip the
    // business limit it mimics.
    static final int MAX_SEEDED_STUDENTS_PER_COURSE = 30;

    private static final Logger LOG = System.getLogger(SeedRunner.class.getName());

    private final SeedProperties properties;
    private final Faker faker;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SeedRunner self;

    public SeedRunner(SeedProperties properties,
                      Faker faker,
                      StudentRepository studentRepository,
                      TeacherRepository teacherRepository,
                      CourseRepository courseRepository,
                      EnrollmentRepository enrollmentRepository,
                      UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      @Lazy SeedRunner self) {
        this.properties = properties;
        this.faker = faker;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.self = self;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        // Through the bean's own transactional proxy, so every write below is one unit of work: a failure
        // halfway rolls the whole seed back, and the next start finds the database empty and seeds it.
        self.seed();
    }

    @Transactional
    void seed() {
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
        // Each teacher teaches one or two catalogue courses, so every course has a teacher who can take
        // students. When the catalogue is the full 12 and the teachers are many, everyone teaches one.
        int coursesPerTeacher = courses.size() >= 2 * properties.getTeachers() ? 1 : 2;
        List<Teacher> teachers = new ArrayList<>();
        for (int i = 0; i < properties.getTeachers(); i++) {
            Teacher teacher = new Teacher(faker.name().fullName(), "seed-teacher-" + i + "@example.com",
                    i < properties.getCurators());
            for (int k = 0; k < coursesPerTeacher; k++) {
                teacher.getCourses().add(courses.get((i * coursesPerTeacher + k) % courses.size()));
            }
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
        // One budget per (teacher, course): several seeded teachers can teach the same course, and the
        // business limit counts a pair, not a course.
        Map<TeacherCourse, Integer> load = new HashMap<>();
        int created = 0;
        for (int i = 0; i < students.size(); i++) {
            if (i % 2 != 0) {
                continue;
            }
            Student student = students.get(i);
            // A student is led by one teacher per course. A second slot that would repeat a course (an odd
            // catalogue does that) moves on to the next course instead, so two assignments are placed
            // whenever the catalogue is large enough.
            Set<Course> taken = new HashSet<>();
            for (int offset = 0; offset < courses.size() && taken.size() < 2; offset++) {
                Course course = courses.get((i + offset) % courses.size());
                if (taken.contains(course)) {
                    continue;
                }
                Teacher teacher = nextTeacher(teachers, course, load);
                if (teacher == null) {
                    continue;
                }
                taken.add(course);
                load.merge(new TeacherCourse(teacher, course), 1, Integer::sum);
                enrollmentRepository.save(new Enrollment(teacher, student, course));
                created++;
            }
        }
        return created;
    }

    /**
     * The competent teacher of the course with the free-most places, so a course taught by several seeded
     * teachers gets its students spread over all of them; null when each of them is at the limit.
     */
    private static Teacher nextTeacher(List<Teacher> teachers, Course course, Map<TeacherCourse, Integer> load) {
        return teachers.stream()
                .filter(teacher -> teacher.teaches(course))
                .min(Comparator.comparingInt(teacher -> load.getOrDefault(new TeacherCourse(teacher, course), 0)))
                .filter(teacher -> load.getOrDefault(new TeacherCourse(teacher, course), 0)
                        < MAX_SEEDED_STUDENTS_PER_COURSE)
                .orElse(null);
    }

    // Identity on purpose: the entities are compared by reference, the rows may not have ids yet.
    private record TeacherCourse(Teacher teacher, Course course) {
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
