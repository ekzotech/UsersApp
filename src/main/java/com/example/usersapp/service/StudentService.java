package com.example.usersapp.service;

import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.StudentRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public List<Student> findAll(String query) {
        Sort byName = Sort.by("fullName");
        if (query == null || query.isBlank()) {
            return studentRepository.findAll(byName);
        }
        String q = query.strip();
        return studentRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(q, q, byName);
    }
}
