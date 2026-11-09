package com.example.usersapp.web;

import com.example.usersapp.service.StudentService;
import com.example.usersapp.service.TeacherService;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/users")
public class UserController {

    private final StudentService studentService;
    private final TeacherService teacherService;

    public UserController(StudentService studentService, TeacherService teacherService) {
        this.studentService = studentService;
        this.teacherService = teacherService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, Authentication authentication, Model model) {
        model.addAttribute("students", studentService.findAll(q));
        model.addAttribute("teachers", teacherService.findAll());
        model.addAttribute("q", q);
        model.addAttribute("canAdminister", isAdmin(authentication));
        return "users/list";
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> Objects.equals(authority.getAuthority(), "ROLE_ADMIN"));
    }
}
