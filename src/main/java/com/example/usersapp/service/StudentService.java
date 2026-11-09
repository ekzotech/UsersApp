package com.example.usersapp.service;

import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.StudentRepository;
import com.example.usersapp.web.StudentForm;
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
        List<Student> students = query == null || query.isBlank()
                ? studentRepository.findAll(byName)
                : studentRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        query.strip(), query.strip(), byName);
        // The list page renders the curator's name after this transaction ends (open-in-view=false).
        students.forEach(student -> {
            if (student.getCurator() != null) {
                student.getCurator().getFullName();
            }
        });
        return students;
    }

    @Transactional
    public Student create(StudentForm form) {
        String fullName = form.getFullName().strip();
        String email = form.getEmail().strip();
        String groupName = form.getGroupName().strip();
        if (studentRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException(email);
        }
        return studentRepository.save(new Student(fullName, email, groupName));
    }

    @Transactional
    public Student activate(Long id) {
        return setActive(id, true);
    }

    @Transactional
    public Student deactivate(Long id) {
        return setActive(id, false);
    }

    private Student setActive(Long id, boolean active) {
        Student student = studentRepository.findById(id).orElseThrow(() -> new StudentNotFoundException(id));
        student.setActive(active);
        return studentRepository.save(student);
    }
}
