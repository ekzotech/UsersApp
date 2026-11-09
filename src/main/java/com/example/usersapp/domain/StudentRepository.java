package com.example.usersapp.domain;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String fullName, String email, Sort sort);

    boolean existsByEmailIgnoreCase(String email);

    List<Student> findByCuratorIdOrderByFullName(Long curatorId);

    List<Student> findByCuratorIsNullAndActiveTrueOrderByFullName();
}
