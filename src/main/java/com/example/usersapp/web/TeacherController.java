package com.example.usersapp.web;

import com.example.usersapp.service.DuplicateEmailException;
import com.example.usersapp.service.DuplicateUsernameException;
import com.example.usersapp.service.TeacherService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/teachers")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("teachers", teacherService.findAll());
        return "teachers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new TeacherForm());
        return "teachers/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") TeacherForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "teachers/form";
        }
        try {
            teacherService.create(form);
        } catch (DuplicateEmailException e) {
            bindingResult.rejectValue("email", "duplicate", e.getMessage());
            return "teachers/form";
        } catch (DuplicateUsernameException e) {
            bindingResult.rejectValue("username", "duplicate", e.getMessage());
            return "teachers/form";
        }
        redirectAttributes.addFlashAttribute("success", "Преподаватель создан");
        return "redirect:/users";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        teacherService.activate(id);
        redirectAttributes.addFlashAttribute("success", "Преподаватель активирован");
        return "redirect:/users";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        teacherService.deactivate(id);
        redirectAttributes.addFlashAttribute("success", "Преподаватель уволен");
        return "redirect:/users";
    }
}
