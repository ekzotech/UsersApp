package com.example.usersapp.db;

import static com.example.usersapp.domain.CourseFixtures.course;
import static com.example.usersapp.domain.TeacherFixtures.teacher;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.usersapp.domain.Course;
import com.example.usersapp.domain.CourseRepository;
import com.example.usersapp.domain.Enrollment;
import com.example.usersapp.domain.EnrollmentRepository;
import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.StudentRepository;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherRepository;
import com.example.usersapp.support.PostgresTestBase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EnrollmentRepositoryIT extends PostgresTestBase {

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @BeforeEach
    void cleanTables() {
        enrollmentRepository.deleteAll();
        studentRepository.deleteAll();
        teacherRepository.deleteAll();
        courseRepository.deleteAll();
    }

    @Test
    void findsEnrollmentsOfTeacherByCourseTitleThenStudentName() {
        Teacher teacher = teacher("Иван Предметников", "ivan-enroll@example.com");
        Course algebra = courseRepository.saveAndFlush(course("algebra", "Алгебра"));
        Course geometry = courseRepository.saveAndFlush(course("geometry", "Геометрия"));
        teacher.getCourses().addAll(List.of(algebra, geometry));
        teacherRepository.saveAndFlush(teacher);
        Student zoe = studentRepository.saveAndFlush(new Student("Яна Зуева", "zoe@example.com", "ИС-11"));
        Student anna = studentRepository.saveAndFlush(new Student("Анна Смирнова", "anna-e@example.com", "ИС-11"));
        enrollmentRepository.saveAll(List.of(
                new Enrollment(teacher, zoe, geometry),
                new Enrollment(teacher, zoe, algebra),
                new Enrollment(teacher, anna, algebra)));
        enrollmentRepository.flush();

        List<Enrollment> rows = enrollmentRepository
                .findByTeacherIdOrderByCourseTitleAscStudentFullNameAsc(teacher.getId());

        assertThat(rows)
                .extracting(e -> e.getCourse().getTitle() + " / " + e.getStudent().getFullName())
                .containsExactly(
                        "Алгебра / Анна Смирнова",
                        "Алгебра / Яна Зуева",
                        "Геометрия / Яна Зуева");
    }

    @Test
    void countsEnrollmentsByTeacherAndCourse() {
        Teacher teacher = teacher("Иван Предметников", "ivan-count@example.com");
        Teacher other = teacher("Пётр Другой", "petr-count@example.com");
        Course algebra = courseRepository.saveAndFlush(course("algebra-c", "Алгебра"));
        Course geometry = courseRepository.saveAndFlush(course("geometry-c", "Геометрия"));
        teacher.getCourses().addAll(List.of(algebra, geometry));
        other.getCourses().addAll(List.of(algebra, geometry));
        teacherRepository.saveAll(List.of(teacher, other));
        teacherRepository.flush();
        Student first = studentRepository.saveAndFlush(new Student("Анна Смирнова", "anna-c@example.com", "ИС-11"));
        Student second = studentRepository.saveAndFlush(new Student("Яна Зуева", "zoe-c@example.com", "ИС-11"));
        enrollmentRepository.saveAll(List.of(
                new Enrollment(teacher, first, algebra),
                new Enrollment(teacher, second, algebra),
                new Enrollment(other, first, algebra),
                new Enrollment(teacher, first, geometry)));
        enrollmentRepository.flush();

        assertThat(enrollmentRepository.countByTeacherIdAndCourseId(teacher.getId(), algebra.getId()))
                .isEqualTo(2L);
        assertThat(enrollmentRepository.countByTeacherIdAndCourseId(teacher.getId(), geometry.getId()))
                .isEqualTo(1L);
        assertThat(enrollmentRepository.findByStudentId(first.getId())).hasSize(3);
    }
}
