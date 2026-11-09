package com.example.usersapp.service;

import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.StudentRepository;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherRepository;
import com.example.usersapp.domain.User;
import com.example.usersapp.domain.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CuratorService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;

    public CuratorService(StudentRepository studentRepository,
                          TeacherRepository teacherRepository,
                          UserRepository userRepository) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Student assignCurator(Long studentId, Long curatorId) {
        Student student = studentRepository.findById(studentId).orElseThrow(() -> new StudentNotFoundException(studentId));
        Teacher teacher = teacherRepository.findById(curatorId).orElseThrow(() -> new TeacherNotFoundException(curatorId));
        if (!teacher.isCurator()) {
            throw new CuratorRuleException("Только преподаватель с ролью «Куратор» может быть куратором");
        }
        if (!teacher.isActive()) {
            throw new CuratorRuleException("Нельзя назначить уволенного преподавателя");
        }
        if (!student.isActive()) {
            throw new CuratorRuleException("Нельзя назначить куратора отчисленному студенту");
        }
        student.setCurator(teacher);
        return studentRepository.save(student);
    }

    @Transactional
    public Student removeCurator(Long studentId) {
        Student student = studentRepository.findById(studentId).orElseThrow(() -> new StudentNotFoundException(studentId));
        student.setCurator(null);
        return studentRepository.save(student);
    }

    @Transactional(readOnly = true)
    public List<Student> studentsOfCurator(String username) {
        Teacher teacher = curatorOf(username).orElseThrow(AccessDeniedForCuratorException::new);
        List<Student> students = studentRepository.findByCuratorIdOrderByFullName(teacher.getId());
        // The page renders the curator's name after this transaction ends (open-in-view=false).
        students.forEach(student -> {
            if (student.getCurator() != null) {
                student.getCurator().getFullName();
            }
        });
        return students;
    }

    @Transactional(readOnly = true)
    public void assertCuratorOwnsStudent(String username, Long studentId) {
        Student student = studentRepository.findById(studentId).orElseThrow(() -> new StudentNotFoundException(studentId));
        Long curatorId = curatorOf(username)
                .map(Teacher::getId)
                .orElseThrow(AccessDeniedForCuratorException::new);
        if (student.getCurator() == null || !Objects.equals(student.getCurator().getId(), curatorId)) {
            throw new AccessDeniedForCuratorException();
        }
    }

    private Optional<Teacher> curatorOf(String username) {
        // Fail closed: every login account carries ROLE_TEACHER, so the URL-level role check does not
        // prove the account's teacher is an active curator (amendment (9)).
        return userRepository.findByUsername(username)
                .map(User::getTeacher)
                .map(teacher -> (Teacher) Hibernate.unproxy(teacher))
                .filter(Objects::nonNull)
                .filter(Teacher::isCurator)
                .filter(Teacher::isActive);
    }
}
