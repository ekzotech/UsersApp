package com.example.usersapp.web;

import com.example.usersapp.domain.Course;
import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.service.CourseService;
import com.example.usersapp.service.EnrollmentRuleException;
import com.example.usersapp.service.EnrollmentService;
import com.example.usersapp.service.StudentService;
import com.example.usersapp.service.TeacherService;
import java.util.Comparator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/teachers")
public class TeacherProfileController {

    private final TeacherService teacherService;
    private final EnrollmentService enrollmentService;
    private final StudentService studentService;
    private final CourseService courseService;

    public TeacherProfileController(TeacherService teacherService,
                                    EnrollmentService enrollmentService,
                                    StudentService studentService,
                                    CourseService courseService) {
        this.teacherService = teacherService;
        this.enrollmentService = enrollmentService;
        this.studentService = studentService;
        this.courseService = courseService;
    }

    @GetMapping("/{id}")
    public String profile(@PathVariable Long id, Model model) {
        Teacher teacher = teacherService.findById(id);
        model.addAttribute("teacher", teacher);
        model.addAttribute("enrollments", enrollmentService.studentsOfTeacher(id));
        // The assign form offers active students only: a dismissed student is rejected on submit.
        model.addAttribute("students", studentService.findAll(null).stream().filter(Student::isActive).toList());
        // teacher.courses is an unordered Set; the badges and the per-course forms sort by title.
        model.addAttribute("coursesByTitle", teacher.getCourses().stream()
                .sorted(Comparator.comparing(Course::getTitle))
                .toList());
        return "teachers/profile";
    }

    @PostMapping("/{id}/courses/{courseId}/students")
    public String enroll(@PathVariable Long id,
                         @PathVariable Long courseId,
                         @RequestParam Long studentId,
                         RedirectAttributes redirectAttributes) {
        try {
            enrollmentService.enroll(id, courseId, studentId);
            redirectAttributes.addFlashAttribute("success", "Студент назначен");
        } catch (EnrollmentRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teachers/{id}";
    }
}
