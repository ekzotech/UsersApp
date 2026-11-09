package com.example.usersapp.service;

import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherRepository;
import com.example.usersapp.domain.User;
import com.example.usersapp.domain.UserRepository;
import com.example.usersapp.domain.UserRole;
import com.example.usersapp.web.TeacherForm;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TeacherService(TeacherRepository teacherRepository,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.teacherRepository = teacherRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<Teacher> findAll() {
        return teacherRepository.findAll(Sort.by("fullName"));
    }

    @Transactional
    public Teacher create(TeacherForm form) {
        String fullName = form.getFullName().strip();
        String email = form.getEmail().strip();
        String username = form.getUsername().strip();
        // Both checks run before the first insert: a duplicate must not leave a half-written teacher.
        if (teacherRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException(email);
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateUsernameException(username);
        }
        Teacher saved = teacherRepository.save(new Teacher(fullName, email, form.isCurator()));

        // The delegating encoder stores "{bcrypt}$2a$...", which CustomUserDetailsService hands to
        // the login flow as is; never a bare BCrypt hash.
        User account = new User(username, passwordEncoder.encode(form.getPassword()), UserRole.TEACHER);
        account.setTeacher(saved);
        userRepository.save(account);
        return saved;
    }

    @Transactional
    public Teacher activate(Long id) {
        return setActive(id, true);
    }

    @Transactional
    public Teacher deactivate(Long id) {
        return setActive(id, false);
    }

    private Teacher setActive(Long id, boolean active) {
        Teacher teacher = teacherRepository.findById(id).orElseThrow(() -> new TeacherNotFoundException(id));
        teacher.setActive(active);
        return teacherRepository.save(teacher);
    }
}
