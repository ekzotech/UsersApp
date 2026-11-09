package com.example.usersapp.service;

import com.example.usersapp.domain.Course;
import com.example.usersapp.domain.CourseRepository;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final TeacherRepository teacherRepository;

    public CourseService(CourseRepository courseRepository, TeacherRepository teacherRepository) {
        this.courseRepository = courseRepository;
        this.teacherRepository = teacherRepository;
    }

    public List<Course> findAll() {
        return courseRepository.findAll(Sort.by("title"));
    }

    public List<Teacher> curators() {
        return teacherRepository.findByCuratorTrueAndActiveTrueOrderByFullName();
    }
}
