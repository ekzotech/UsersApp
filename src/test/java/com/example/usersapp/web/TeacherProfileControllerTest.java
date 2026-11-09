package com.example.usersapp.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.usersapp.domain.Course;
import com.example.usersapp.domain.CourseFixtures;
import com.example.usersapp.domain.Enrollment;
import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherFixtures;
import com.example.usersapp.service.CourseService;
import com.example.usersapp.service.EnrollmentRuleException;
import com.example.usersapp.service.EnrollmentService;
import com.example.usersapp.service.StudentService;
import com.example.usersapp.service.TeacherNotFoundException;
import com.example.usersapp.service.TeacherService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TeacherProfileController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class TeacherProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeacherService teacherService;

    @MockitoBean
    private EnrollmentService enrollmentService;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private CourseService courseService;

    @Test
    void profileRendersEnrollmentsAndAssignForms() throws Exception {
        Course geometry = course(9L, "Геометрия");
        Course algebra = course(10L, "Алгебра");
        Teacher teacher = teacher(1L, "Иван Предметников", "ivan@example.com", true, algebra, geometry);
        Student enrolled = student(5L, "Анна Смирнова", "anna@example.com");
        Student candidate = student(6L, "Борис Волков", "boris@example.com");
        when(teacherService.findById(1L)).thenReturn(teacher);
        when(enrollmentService.studentsOfTeacher(1L)).thenReturn(List.of(
                new Enrollment(teacher, enrolled, algebra),
                new Enrollment(teacher, candidate, geometry)));
        when(studentService.findAll(null)).thenReturn(List.of(enrolled, candidate));

        mockMvc.perform(get("/teachers/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("teachers/profile"))
                .andExpect(model().attribute("teacher", teacher))
                .andExpect(model().attribute("students", List.of(enrolled, candidate)))
                .andExpect(model().attribute("coursesByTitle", List.of(algebra, geometry)))
                .andExpect(content().string(containsString("Иван Предметников")))
                .andExpect(content().string(containsString("Куратор")))
                .andExpect(content().string(containsString("Геометрия")))
                .andExpect(content().string(containsString("Алгебра")))
                .andExpect(content().string(containsString("Анна Смирнова")))
                .andExpect(content().string(containsString("Борис Волков")))
                .andExpect(content().string(containsString("Студент\">Анна Смирнова")))
                .andExpect(content().string(containsString("Предмет\">Алгебра")))
                .andExpect(content().string(containsString("name=\"studentId\">")))
                .andExpect(content().string(containsString("action=\"/teachers/1/courses/10/students\"")))
                .andExpect(content().string(containsString("action=\"/teachers/1/courses/9/students\"")))
                .andExpect(content().string(containsString("Назначить")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        verify(teacherService).findById(1L);
        verify(enrollmentService).studentsOfTeacher(1L);
        verify(studentService).findAll(null);
    }

    @Test
    void enrollRedirectsWithFlash() throws Exception {
        mockMvc.perform(post("/teachers/1/courses/9/students").param("studentId", "5").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teachers/1"))
                .andExpect(flash().attribute("success", "Студент назначен"));

        verify(enrollmentService).enroll(1L, 9L, 5L);
    }

    @Test
    void enrollRuleViolationShowsFlashError() throws Exception {
        when(enrollmentService.enroll(1L, 9L, 5L)).thenThrow(new EnrollmentRuleException("Студент отчислен"));

        mockMvc.perform(post("/teachers/1/courses/9/students").param("studentId", "5").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teachers/1"))
                .andExpect(flash().attribute("error", "Студент отчислен"));

        verify(enrollmentService).enroll(1L, 9L, 5L);
    }

    @Test
    void profileUnknownIs404() throws Exception {
        when(teacherService.findById(77L)).thenThrow(new TeacherNotFoundException(77L));

        mockMvc.perform(get("/teachers/77")).andExpect(status().isNotFound());

        verifyNoInteractions(enrollmentService);
    }

    @Test
    void enrollWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/teachers/1/courses/9/students").param("studentId", "5"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(teacherService, enrollmentService, studentService, courseService);
    }

    @Test
    void teacherRequestsWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/teachers/2/activate")).andExpect(status().isForbidden());
        mockMvc.perform(post("/teachers/2/deactivate")).andExpect(status().isForbidden());
        verifyNoInteractions(teacherService);
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/teachers/1")).andExpect(status().isForbidden());

        mockMvc.perform(post("/teachers/1/courses/9/students").param("studentId", "5").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(teacherService, enrollmentService, studentService, courseService);
    }

    @Test
    void profileForDismissedTeacherWithoutCoursesStillRenders() throws Exception {
        Teacher dismissed = teacher(2L, "Пётр Петров", "petrov@example.com", false);
        dismissed.setActive(false);
        when(teacherService.findById(2L)).thenReturn(dismissed);
        when(enrollmentService.studentsOfTeacher(2L)).thenReturn(List.of());
        when(studentService.findAll(null)).thenReturn(List.of());

        mockMvc.perform(get("/teachers/2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Уволен")))
                .andExpect(content().string(not(containsString("Куратор"))))
                .andExpect(content().string(containsString("Нет назначенных студентов")));
    }

    @Test
    void profileEscapesStudentNames() throws Exception {
        Course algebra = course(9L, "Алгебра");
        Teacher teacher = teacher(1L, "Иван Предметников", "ivan@example.com", false, algebra);
        Student hostile = student(5L, "<script>alert(1)</script>", "x@example.com");
        when(teacherService.findById(1L)).thenReturn(teacher);
        when(enrollmentService.studentsOfTeacher(1L)).thenReturn(List.of(new Enrollment(teacher, hostile, algebra)));
        when(studentService.findAll(null)).thenReturn(List.of(hostile));

        mockMvc.perform(get("/teachers/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    @Test
    void enrollCourseNotTaughtByTeacherShowsFlashError() throws Exception {
        doThrow(new EnrollmentRuleException("Предмет не закреплён за преподавателем"))
                .when(enrollmentService).enroll(1L, 9L, 5L);

        mockMvc.perform(post("/teachers/1/courses/9/students").param("studentId", "5").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teachers/1"))
                .andExpect(flash().attribute("error", "Предмет не закреплён за преподавателем"));
    }

    @Test
    void enrollRequiresStudentId() throws Exception {
        mockMvc.perform(post("/teachers/1/courses/9/students").with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(enrollmentService);
    }

    private static Teacher teacher(Long id, String fullName, String email, boolean curator, Course... courses) {
        Teacher teacher = TeacherFixtures.teacher(fullName, email);
        teacher.setId(id);
        teacher.setCurator(curator);
        teacher.getCourses().addAll(List.of(courses));
        return teacher;
    }

    private static Course course(Long id, String title) {
        Course course = CourseFixtures.course("course-" + id, title);
        course.setId(id);
        return course;
    }

    private static Student student(Long id, String fullName, String email) {
        Student student = new Student(fullName, email, "ИС-11");
        student.setId(id);
        return student;
    }
}
