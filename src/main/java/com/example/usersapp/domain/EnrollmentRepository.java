package com.example.usersapp.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByTeacherIdAndStudentIdAndCourseId(Long teacherId, Long studentId, Long courseId);

    long countByTeacherIdAndCourseId(Long teacherId, Long courseId);

    List<Enrollment> findByStudentId(Long studentId);

    // The teacher profile renders student and course names after the service transaction ends
    // (open-in-view=false), so the rows arrive with both associations initialised. Ordering is still
    // derived from the associations (course title, then student name), so the query stays derived.
    @EntityGraph(attributePaths = {"student", "course"})
    List<Enrollment> findByTeacherIdOrderByCourseTitleAscStudentFullNameAsc(Long teacherId);
}
