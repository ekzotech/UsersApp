package com.example.usersapp.service;

import com.example.usersapp.domain.Course;
import com.example.usersapp.domain.CourseRepository;
import com.example.usersapp.domain.Enrollment;
import com.example.usersapp.domain.EnrollmentRepository;
import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.StudentRepository;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentService {

    public static final int MAX_STUDENTS_PER_COURSE_PER_TEACHER = 60;

    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final CourseRepository courseRepository;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             StudentRepository studentRepository,
                             TeacherRepository teacherRepository,
                             CourseRepository courseRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public Enrollment enroll(Long teacherId, Long courseId, Long studentId) {
        Teacher teacher = teacherRepository.findWithCoursesById(teacherId)
                .orElseThrow(() -> new TeacherNotFoundException(teacherId));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        if (!teacher.teaches(course)) {
            throw new EnrollmentRuleException("Предмет не закреплён за преподавателем");
        }
        if (!teacher.isActive()) {
            throw new EnrollmentRuleException("Преподаватель уволен");
        }
        if (!student.isActive()) {
            throw new EnrollmentRuleException("Студент отчислен");
        }
        // A student may be led by different teachers in different subjects, but within one subject
        // only one teacher is responsible: the same course with another teacher is a conflict.
        boolean otherTeacherOfCourse = enrollmentRepository.findByStudentId(studentId).stream()
                .anyMatch(existing -> Objects.equals(existing.getCourse().getId(), courseId)
                        && !Objects.equals(existing.getTeacher().getId(), teacherId));
        if (otherTeacherOfCourse) {
            throw new EnrollmentRuleException("Этот предмет у студента уже ведёт другой преподаватель");
        }
        if (enrollmentRepository.findByTeacherIdAndStudentIdAndCourseId(teacherId, studentId, courseId).isPresent()) {
            throw new EnrollmentRuleException("Студент уже назначен");
        }
        long assigned = enrollmentRepository.countByTeacherIdAndCourseId(teacherId, courseId);
        if (assigned >= MAX_STUDENTS_PER_COURSE_PER_TEACHER) {
            throw new EnrollmentRuleException(
                    "Преподаватель уже ведёт " + MAX_STUDENTS_PER_COURSE_PER_TEACHER + " студентов по этому предмету");
        }
        return enrollmentRepository.save(new Enrollment(teacher, student, course));
    }

    @Transactional(readOnly = true)
    public List<Enrollment> studentsOfTeacher(Long teacherId) {
        // The profile page renders student names and course titles after this transaction ends
        // (open-in-view=false); the repository query fetches both associations eagerly.
        return enrollmentRepository.findByTeacherIdOrderByCourseTitleAscStudentFullNameAsc(teacherId);
    }
}
