package com.example.usersapp.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
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

import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherFixtures;
import com.example.usersapp.service.DuplicateEmailException;
import com.example.usersapp.service.DuplicateUsernameException;
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

@WebMvcTest(TeacherController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class TeacherControllerTest {

    private static final String FULL_NAME = "Иван Иванов";
    private static final String EMAIL = "ivanov@example.com";
    private static final String USERNAME = "ivanov";
    private static final String PASSWORD = "secret123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeacherService teacherService;

    @Test
    void getNewFormRenders() throws Exception {
        mockMvc.perform(get("/teachers/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("teachers/form"))
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("type=\"password\"")));
    }

    @Test
    void postValidTeacherRedirects() throws Exception {
        when(teacherService.create(any(TeacherForm.class))).thenReturn(active("Иван Иванов", EMAIL, true, 1L));

        mockMvc.perform(post("/teachers/new")
                        .param("fullName", FULL_NAME)
                        .param("email", EMAIL)
                        .param("username", USERNAME)
                        .param("password", PASSWORD)
                        .param("curator", "true")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("success", "Преподаватель создан"));
    }

    @Test
    void postShortPasswordRedisplays() throws Exception {
        mockMvc.perform(post("/teachers/new")
                        .param("fullName", FULL_NAME)
                        .param("email", EMAIL)
                        .param("username", USERNAME)
                        .param("password", "short")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("teachers/form"))
                .andExpect(model().attributeHasFieldErrors("form", "password"))
                .andExpect(content().string(containsString("Пароль: минимум 8 символов")));

        verifyNoInteractions(teacherService);
    }

    @Test
    void postBlankUsernameRedisplays() throws Exception {
        mockMvc.perform(post("/teachers/new")
                        .param("fullName", FULL_NAME)
                        .param("email", EMAIL)
                        .param("username", "   ")
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("teachers/form"))
                .andExpect(model().attributeHasFieldErrors("form", "username"));

        verifyNoInteractions(teacherService);
    }

    @Test
    void postDuplicateEmailRedisplays() throws Exception {
        when(teacherService.create(any(TeacherForm.class))).thenThrow(new DuplicateEmailException(EMAIL));

        mockMvc.perform(post("/teachers/new")
                        .param("fullName", FULL_NAME)
                        .param("email", EMAIL)
                        .param("username", USERNAME)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("teachers/form"))
                .andExpect(model().attributeHasFieldErrors("form", "email"))
                .andExpect(model().attributeHasFieldErrorCode("form", "email", "duplicate"));
    }

    @Test
    void postDuplicateUsernameRedisplays() throws Exception {
        when(teacherService.create(any(TeacherForm.class))).thenThrow(new DuplicateUsernameException(USERNAME));

        mockMvc.perform(post("/teachers/new")
                        .param("fullName", FULL_NAME)
                        .param("email", EMAIL)
                        .param("username", USERNAME)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("teachers/form"))
                .andExpect(model().attributeHasFieldErrors("form", "username"))
                .andExpect(model().attributeHasFieldErrorCode("form", "username", "duplicate"));
    }

    @Test
    void getTeacherListRendersLinksToProfiles() throws Exception {
        Teacher curator = active("Пётр Кураторов", "curator@example.com", true, 3L);
        Teacher lecturer = active("Иван Иванов", "ivanov@example.com", false, 4L);
        when(teacherService.findAll()).thenReturn(List.of(curator, lecturer));

        mockMvc.perform(get("/teachers"))
                .andExpect(status().isOk())
                .andExpect(view().name("teachers/list"))
                .andExpect(model().attribute("teachers", List.of(curator, lecturer)))
                .andExpect(content().string(containsString("href=\"/teachers/3\"")))
                .andExpect(content().string(containsString("href=\"/teachers/4\"")))
                .andExpect(content().string(containsString("action=\"/teachers/3/deactivate\"")))
                .andExpect(content().string(containsString("Пётр Кураторов")))
                .andExpect(content().string(containsString("Куратор")));
    }

    @Test
    void getTeacherListOffersActivationForDismissedTeacher() throws Exception {
        Teacher dismissed = active("Пётр Петров", "petrov@example.com", false, 5L);
        dismissed.setActive(false);
        when(teacherService.findAll()).thenReturn(List.of(dismissed));

        mockMvc.perform(get("/teachers"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Уволен")))
                .andExpect(content().string(containsString("action=\"/teachers/5/activate\"")))
                .andExpect(content().string(not(containsString("action=\"/teachers/5/deactivate\""))));
    }

    @Test
    void postDeactivateTeacherRedirects() throws Exception {
        mockMvc.perform(post("/teachers/1/deactivate").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("success", "Преподаватель уволен"));

        verify(teacherService).deactivate(1L);
    }

    @Test
    void postActivateTeacherRedirects() throws Exception {
        mockMvc.perform(post("/teachers/2/activate").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("success", "Преподаватель активирован"));

        verify(teacherService).activate(2L);
    }

    @Test
    void deactivateUnknownIs404() throws Exception {
        when(teacherService.deactivate(99L)).thenThrow(new TeacherNotFoundException(99L));

        mockMvc.perform(post("/teachers/99/deactivate").with(csrf()))
                .andExpect(status().isNotFound());

        verify(teacherService).deactivate(99L);
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/teachers/1/deactivate")).andExpect(status().isForbidden());

        verify(teacherService, never()).deactivate(anyLong());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherRoleIsForbiddenToCreate() throws Exception {
        mockMvc.perform(get("/teachers/new")).andExpect(status().isForbidden());

        mockMvc.perform(post("/teachers/new")
                        .param("fullName", FULL_NAME)
                        .param("email", EMAIL)
                        .param("username", USERNAME)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(teacherService);
    }

    private static Teacher active(String fullName, String email, boolean curator, Long id) {
        Teacher teacher = TeacherFixtures.teacher(fullName, email);
        teacher.setId(id);
        teacher.setCurator(curator);
        return teacher;
    }
}
