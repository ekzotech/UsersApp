package com.example.usersapp.service;

import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherService {

    private final TeacherRepository teacherRepository;

    public TeacherService(TeacherRepository teacherRepository) {
        this.teacherRepository = teacherRepository;
    }

    @Transactional(readOnly = true)
    public List<Teacher> findAll() {
        return teacherRepository.findAll(Sort.by("fullName"));
    }
}
