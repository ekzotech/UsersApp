package com.example.usersapp.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByTeacherIdAndStudentIdAndCourseId(Long teacherId, Long studentId, Long courseId);

    long countByTeacherIdAndCourseId(Long teacherId, Long courseId);

    List<Enrollment> findByStudentId(Long studentId);

    List<Enrollment> findByTeacherIdOrderByCourseTitleAscStudentFullNameAsc(Long teacherId);
}
