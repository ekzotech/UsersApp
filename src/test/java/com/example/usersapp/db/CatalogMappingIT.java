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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogMappingIT extends PostgresTestBase {

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Test
    void teacherCoursesAreLinkedAndFetchedWithEntityGraph() {
        Course itCourse = courseRepository.saveAndFlush(course("it-course", "Интеграционный предмет"));

        Teacher owner = teacher("Пётр Кураторов", "teacher@example.com");
        owner.getCourses().add(itCourse);
        teacherRepository.saveAndFlush(owner);

        Teacher loaded = teacherRepository.findWithCoursesById(owner.getId()).orElseThrow();

        assertThat(loaded.getId()).isEqualTo(owner.getId());
        assertThat(loaded.getCourses()).extracting(Course::getId).containsExactly(itCourse.getId());
        assertThat(loaded.teaches(itCourse)).isTrue();
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.getDisplayStatus()).isEqualTo("Активен");

        Course other = courseRepository.saveAndFlush(course("other-course", "Другой предмет"));
        assertThat(loaded.teaches(other)).isFalse();
    }

    @Test
    void studentCuratorIsPersistedAndEnrollmentsAreQueryable() {
        Teacher mentor = teacherRepository.saveAndFlush(teacher("Ирина Предметова", "irina@example.com"));
        Course physics = courseRepository.saveAndFlush(course("it-course", "Интеграционный предмет"));

        Student student = new Student("Анна Смирнова", "anna@example.com", "ИС-11");
        student.setCurator(mentor);
        studentRepository.saveAndFlush(student);

        Student reloaded = studentRepository.findById(student.getId()).orElseThrow();
        assertThat(reloaded.getCurator().getId()).isEqualTo(mentor.getId());
        assertThat(reloaded.getGroupName()).isEqualTo("ИС-11");

        Enrollment enrollment = enrollmentRepository.saveAndFlush(new Enrollment(mentor, student, physics));

        assertThat(enrollment.getId()).isNotNull();
        assertThat(enrollmentRepository.countByTeacherIdAndCourseId(mentor.getId(), physics.getId())).isEqualTo(1);
        assertThat(enrollmentRepository.findByTeacherIdAndStudentIdAndCourseId(
                        mentor.getId(), student.getId(), physics.getId()))
                .hasValueSatisfying(found -> assertThat(found).isEqualTo(enrollment));
        assertThat(enrollmentRepository.findByStudentId(student.getId()))
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.getTeacher().getId()).isEqualTo(mentor.getId());
                    assertThat(found.getCourse().getSlug()).isEqualTo("it-course");
                });
    }
}
