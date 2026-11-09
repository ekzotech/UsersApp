package com.example.usersapp.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherFixtures;
import com.example.usersapp.service.StudentService;
import com.example.usersapp.service.TeacherService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private TeacherService teacherService;

    @Test
    void usersPageRendersStudentsAndTeachers() throws Exception {
        when(studentService.findAll(null)).thenReturn(List.of(
                student("Анна Смирнова", "anna@example.com", true),
                exitedStudent()));
        when(teacherService.findAll()).thenReturn(List.of(curatorTeacher()));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/list"))
                .andExpect(model().attribute("students", List.of(
                        student("Анна Смирнова", "anna@example.com", true),
                        exitedStudent())))
                .andExpect(model().attribute("teachers", List.of(curatorTeacher())))
                .andExpect(content().string(containsString("Анна Смирнова")))
                .andExpect(content().string(containsString("Отчислен")))
                .andExpect(content().string(containsString("Пётр Кураторов")))
                .andExpect(content().string(containsString("Куратор")));
    }

    @Test
    void studentsFilteredByQuery() throws Exception {
        when(studentService.findAll("анн")).thenReturn(List.of(student("Анна Смирнова", "anna@example.com", true)));
        when(teacherService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/users").param("q", "анн"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("q", "анн"));

        verify(studentService).findAll("анн");
    }

    @Test
    void blankQueryListsAll() throws Exception {
        when(studentService.findAll(null)).thenReturn(List.of());
        when(teacherService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("q", (Object) null));

        verify(studentService).findAll(null);
    }

    @Test
    void searchInputKeepsQueryValue() throws Exception {
        when(studentService.findAll("анн")).thenReturn(List.of());
        when(teacherService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/users").param("q", "анн"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"анн\"")));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void usersUrlIsVisibleToTeacher() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/list"));
    }

    @Test
    void studentsListOffersDeactivateForActiveAndActivateForInactive() throws Exception {
        Student active = student("Анна Смирнова", "anna@example.com", true);
        active.setId(1L);
        Student exited = student("Борис Волков", "boris@example.com", false);
        exited.setId(2L);
        when(studentService.findAll(null)).thenReturn(List.of(active, exited));
        when(teacherService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/students/1/deactivate\"")))
                .andExpect(content().string(not(containsString("action=\"/students/1/activate\""))))
                .andExpect(content().string(containsString("action=\"/students/2/activate\"")))
                .andExpect(content().string(not(containsString("action=\"/students/2/deactivate\""))));
    }

    @Test
    void studentsListRendersCuratorNameOfStudent() throws Exception {
        Student curated = student("Анна Смирнова", "anna@example.com", true);
        curated.setCurator(curatorTeacher());
        when(studentService.findAll(null)).thenReturn(List.of(curated));
        when(teacherService.findAll()).thenReturn(List.of(curatorTeacher()));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<td data-label=\"Куратор\">Пётр Кураторов</td>")));
    }

    private static Student student(String fullName, String email, boolean active) {
        Student student = new Student(fullName, email, "ИС-11");
        student.setActive(active);
        return student;
    }

    private static Student exitedStudent() {
        return student("Борис Волков", "boris@example.com", false);
    }

    private static Teacher curatorTeacher() {
        Teacher teacher = TeacherFixtures.teacher("Пётр Кураторов", "curator@example.com");
        teacher.setId(3L);
        teacher.setCurator(true);
        return teacher;
    }
}
