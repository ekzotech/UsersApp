package com.example.usersapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.usersapp.domain.Course;
import com.example.usersapp.domain.CourseFixtures;
import com.example.usersapp.domain.CourseRepository;
import com.example.usersapp.domain.Enrollment;
import com.example.usersapp.domain.EnrollmentRepository;
import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.StudentRepository;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherFixtures;
import com.example.usersapp.domain.TeacherRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    private static final long TEACHER_ID = 7L;
    private static final long COURSE_ID = 3L;
    private static final long STUDENT_ID = 1L;
    private static final long OTHER_TEACHER_ID = 8L;
    private static final long OTHER_COURSE_ID = 4L;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private EnrollmentService service;

    @Test
    void enrollSavesEnrollment() {
        Teacher teacher = teacher(COURSE_ID);
        Course course = course(COURSE_ID);
        Student student = student();
        stubValidRules(teacher, course, student);
        when(enrollmentRepository.countByTeacherIdAndCourseId(TEACHER_ID, COURSE_ID)).thenReturn(0L);
        when(enrollmentRepository.save(any(Enrollment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Enrollment enrollment = service.enroll(TEACHER_ID, COURSE_ID, STUDENT_ID);

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getTeacher()).isSameAs(teacher);
        assertThat(captor.getValue().getStudent()).isSameAs(student);
        assertThat(captor.getValue().getCourse()).isSameAs(course);
        assertThat(enrollment).isSameAs(captor.getValue());
    }

    @Test
    void enrollUnknownTeacherThrows() {
        when(teacherRepository.findWithCoursesById(TEACHER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enroll(TEACHER_ID, COURSE_ID, STUDENT_ID))
                .isInstanceOf(TeacherNotFoundException.class);

        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    void enrollUnknownCourseThrows() {
        Teacher teacher = teacher(COURSE_ID);
        when(teacherRepository.findWithCoursesById(TEACHER_ID)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enroll(TEACHER_ID, COURSE_ID, STUDENT_ID))
                .isInstanceOf(CourseNotFoundException.class);

        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    void enrollUnknownStudentThrows() {
        Teacher teacher = teacher(COURSE_ID);
        when(teacherRepository.findWithCoursesById(TEACHER_ID)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course(COURSE_ID)));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enroll(TEACHER_ID, COURSE_ID, STUDENT_ID))
                .isInstanceOf(StudentNotFoundException.class);

        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    void enrollRejectsCourseNotTaught() {
        Teacher teacher = teacher(99L);
        Course course = course(COURSE_ID);
        when(teacherRepository.findWithCoursesById(TEACHER_ID)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student()));

        assertThatThrownBy(() -> service.enroll(TEACHER_ID, COURSE_ID, STUDENT_ID))
                .isInstanceOf(EnrollmentRuleException.class)
                .hasMessage("Предмет не закреплён за преподавателем");

        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    void enrollRejectsInactiveTeacher() {
        Teacher teacher = teacher(COURSE_ID);
        teacher.setActive(false);
        Course course = course(COURSE_ID);
        when(teacherRepository.findWithCoursesById(TEACHER_ID)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student()));

        assertThatThrownBy(() -> service.enroll(TEACHER_ID, COURSE_ID, STUDENT_ID))
                .isInstanceOf(EnrollmentRuleException.class)
                .hasMessage("Преподаватель уволен");

        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    void enrollRejectsInactiveStudent() {
        Teacher teacher = teacher(COURSE_ID);
        Course course = course(COURSE_ID);
        Student student = student();
        student.setActive(false);
        when(teacherRepository.findWithCoursesById(TEACHER_ID)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.enroll(TEACHER_ID, COURSE_ID, STUDENT_ID))
                .isInstanceOf(EnrollmentRuleException.class)
                .hasMessage("Студент отчислен");

        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    void enrollRejectsDuplicate() {
        Teacher teacher = teacher(COURSE_ID);
        Course course = course(COURSE_ID);
        Student student = student();
        stubValidRules(teacher, course, student);
        when(enrollmentRepository.findByTeacherIdAndStudentIdAndCourseId(TEACHER_ID, STUDENT_ID, COURSE_ID))
                .thenReturn(Optional.of(new Enrollment(teacher, student, course)));

        assertThatThrownBy(() -> service.enroll(TEACHER_ID, COURSE_ID, STUDENT_ID))
                .isInstanceOf(EnrollmentRuleException.class)
                .hasMessage("Студент уже назначен");

        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    void enrollRejectsCourseAlreadyLedByAnotherTeacher() {
        Teacher teacher = teacher(COURSE_ID);
        Course course = course(COURSE_ID);
        Student student = student();
        stubLookups(teacher, course, student);
        when(enrollmentRepository.findByStudentId(STUDENT_ID))
                .thenReturn(List.of(new Enrollment(teacher(OTHER_TEACHER_ID), student, course)));

        assertThatThrownBy(() -> service.enroll(TEACHER_ID, COURSE_ID, STUDENT_ID))
                .isInstanceOf(EnrollmentRuleException.class)
                .hasMessage("Этот предмет у студента уже ведёт другой преподаватель");

        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    void enrollAllowsDifferentCoursesWithDifferentTeachers() {
        Teacher teacher = teacher(COURSE_ID);
        Course course = course(COURSE_ID);
        Student student = student();
        stubValidRules(teacher, course, student);
        Course otherCourse = course(OTHER_COURSE_ID);
        when(enrollmentRepository.findByStudentId(STUDENT_ID))
                .thenReturn(List.of(new Enrollment(teacher(OTHER_COURSE_ID), student, otherCourse)));
        when(enrollmentRepository.countByTeacherIdAndCourseId(TEACHER_ID, COURSE_ID)).thenReturn(0L);
        when(enrollmentRepository.save(any(Enrollment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> service.enroll(TEACHER_ID, COURSE_ID, STUDENT_ID)).doesNotThrowAnyException();

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getCourse()).isSameAs(course);
    }

    @Test
    void enrollRejectsWhenLimitReached() {
        Teacher teacher = teacher(COURSE_ID);
        Course course = course(COURSE_ID);
        Student student = student();
        stubValidRules(teacher, course, student);
        when(enrollmentRepository.countByTeacherIdAndCourseId(TEACHER_ID, COURSE_ID))
                .thenReturn((long) EnrollmentService.MAX_STUDENTS_PER_COURSE_PER_TEACHER);

        assertThatThrownBy(() -> service.enroll(TEACHER_ID, COURSE_ID, STUDENT_ID))
                .isInstanceOf(EnrollmentRuleException.class)
                .hasMessage("Преподаватель уже ведёт 60 студентов по этому предмету");

        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    void enrollAcceptsWhenBelowLimit() {
        Teacher teacher = teacher(COURSE_ID);
        Course course = course(COURSE_ID);
        Student student = student();
        stubValidRules(teacher, course, student);
        when(enrollmentRepository.countByTeacherIdAndCourseId(TEACHER_ID, COURSE_ID)).thenReturn(59L);
        when(enrollmentRepository.save(any(Enrollment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> service.enroll(TEACHER_ID, COURSE_ID, STUDENT_ID)).doesNotThrowAnyException();

        verify(enrollmentRepository).save(any(Enrollment.class));
    }

    @Test
    void studentsOfTeacherDelegates() {
        List<Enrollment> rows = List.of();
        when(enrollmentRepository.findByTeacherIdOrderByCourseTitleAscStudentFullNameAsc(TEACHER_ID))
                .thenReturn(rows);

        assertThat(service.studentsOfTeacher(TEACHER_ID)).isSameAs(rows);
    }

    private void stubLookups(Teacher teacher, Course course, Student student) {
        when(teacherRepository.findWithCoursesById(TEACHER_ID)).thenReturn(Optional.of(teacher));
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
    }

    private void stubValidRules(Teacher teacher, Course course, Student student) {
        stubLookups(teacher, course, student);
        when(enrollmentRepository.findByTeacherIdAndStudentIdAndCourseId(TEACHER_ID, STUDENT_ID, COURSE_ID))
                .thenReturn(Optional.empty());
    }

    private static Teacher teacher(long courseId) {
        Teacher teacher = TeacherFixtures.teacher("Иван Предметников", "ivan@example.com");
        teacher.setId(1L);
        if (courseId == OTHER_COURSE_ID) {
            teacher.setId(OTHER_TEACHER_ID);
        }
        teacher.getCourses().add(course(courseId));
        return teacher;
    }

    private static Course course(long id) {
        Course course = CourseFixtures.course("slug-" + id, "Предмет " + id);
        course.setId(id);
        return course;
    }

    private static Student student() {
        Student student = new Student("Анна Смирнова", "anna@example.com", "ИС-11");
        student.setId(STUDENT_ID);
        return student;
    }
}
