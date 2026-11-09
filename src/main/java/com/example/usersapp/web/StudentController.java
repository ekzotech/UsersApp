package com.example.usersapp.web;

import com.example.usersapp.service.DuplicateEmailException;
import com.example.usersapp.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new StudentForm());
        return "students/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") StudentForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "students/form";
        }
        try {
            studentService.create(form);
        } catch (DuplicateEmailException e) {
            bindingResult.rejectValue("email", "duplicate", e.getMessage());
            return "students/form";
        }
        redirectAttributes.addFlashAttribute("success", "Студент создан");
        return "redirect:/users";
    }
}
