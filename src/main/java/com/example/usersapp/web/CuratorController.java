package com.example.usersapp.web;

import com.example.usersapp.service.CourseService;
import com.example.usersapp.service.CuratorRuleException;
import com.example.usersapp.service.CuratorService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/curator")
public class CuratorController {

    private final CuratorService curatorService;
    private final CourseService courseService;

    public CuratorController(CuratorService curatorService, CourseService courseService) {
        this.curatorService = curatorService;
        this.courseService = courseService;
    }

    @GetMapping("/students")
    public String students(Principal principal, Model model) {
        model.addAttribute("students", curatorService.studentsOfCurator(principal.getName()));
        model.addAttribute("curators", courseService.curators());
        return "curator/students";
    }

    @PostMapping("/students/{id}/curator")
    public String assign(@PathVariable Long id,
                         @RequestParam(required = false) Long curatorId,
                         Principal principal,
                         RedirectAttributes redirectAttributes) {
        // Ownership check first: a foreign or non-curator account must not reach a mutating call.
        curatorService.assertCuratorOwnsStudent(principal.getName(), id);
        try {
            if (curatorId == null) {
                curatorService.removeCurator(id);
                redirectAttributes.addFlashAttribute("success", "Куратор снят");
            } else {
                curatorService.assignCurator(id, curatorId);
                redirectAttributes.addFlashAttribute("success", "Куратор назначен");
            }
        } catch (CuratorRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/curator/students";
    }
}
