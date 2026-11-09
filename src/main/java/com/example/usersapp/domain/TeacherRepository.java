package com.example.usersapp.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    @EntityGraph(attributePaths = "courses")
    Optional<Teacher> findWithCoursesById(Long id);

    boolean existsByEmailIgnoreCase(String email);

    List<Teacher> findByCoursesIdOrderByFullName(Long courseId);

    List<Teacher> findByCuratorTrueAndActiveTrueOrderByFullName();

}
