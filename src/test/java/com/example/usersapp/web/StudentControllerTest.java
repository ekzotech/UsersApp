package com.example.usersapp.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.usersapp.domain.Student;
import com.example.usersapp.service.DuplicateEmailException;
import com.example.usersapp.service.StudentNotFoundException;
import com.example.usersapp.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StudentController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class StudentControllerTest {

    private static final String FULL_NAME = "Анна Смирнова";
    private static final String EMAIL = "student@example.com";
    private static final String GROUP_NAME = "ИС-11";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    @Test
    void getNewFormRenders() throws Exception {
        mockMvc.perform(get("/students/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("students/form"))
                .andExpect(model().attributeExists("form"));
    }

    @Test
    void postValidFormRedirectsToUsersWithFlash() throws Exception {
        when(studentService.create(any(StudentForm.class)))
                .thenReturn(new Student(FULL_NAME, EMAIL, GROUP_NAME));

        mockMvc.perform(post("/students/new")
                        .param("fullName", FULL_NAME)
                        .param("email", EMAIL)
                        .param("groupName", GROUP_NAME)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("success", "Студент создан"));
    }

    @Test
    void postBlankFullNameRedisplaysFormWithError() throws Exception {
        mockMvc.perform(post("/students/new")
                        .param("fullName", "  ")
                        .param("email", EMAIL)
                        .param("groupName", GROUP_NAME)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("students/form"))
                .andExpect(model().attributeHasFieldErrors("form", "fullName"));

        verifyNoInteractions(studentService);
    }

    @Test
    void postInvalidEmailRedisplaysFormWithError() throws Exception {
        mockMvc.perform(post("/students/new")
                        .param("fullName", FULL_NAME)
                        .param("email", "not-an-email")
                        .param("groupName", GROUP_NAME)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("students/form"))
                .andExpect(model().attributeHasFieldErrors("form", "email"));

        verifyNoInteractions(studentService);
    }

    @Test
    void postDuplicateEmailRedisplaysFormWithError() throws Exception {
        when(studentService.create(any(StudentForm.class)))
                .thenThrow(new DuplicateEmailException(EMAIL));

        mockMvc.perform(post("/students/new")
                        .param("fullName", FULL_NAME)
                        .param("email", EMAIL)
                        .param("groupName", GROUP_NAME)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("students/form"))
                .andExpect(model().attributeHasFieldErrors("form", "email"))
                .andExpect(model().attributeHasFieldErrorCode("form", "email", "duplicate"));
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/students/new")
                        .param("fullName", FULL_NAME)
                        .param("email", EMAIL)
                        .param("groupName", GROUP_NAME))
                .andExpect(status().isForbidden());

        verify(studentService, never()).create(any(StudentForm.class));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherIsForbiddenToCreateStudents() throws Exception {
        mockMvc.perform(get("/students/new")).andExpect(status().isForbidden());
    }

    @Test
    void postDeactivateRedirectsWithFlash() throws Exception {
        mockMvc.perform(post("/students/1/deactivate").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("success", "Студент отчислен"));

        verify(studentService).deactivate(1L);
    }

    @Test
    void postActivateRedirectsWithFlash() throws Exception {
        mockMvc.perform(post("/students/2/activate").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("success", "Студент активирован"));

        verify(studentService).activate(2L);
    }

    @Test
    void deactivateUnknownIdIs404() throws Exception {
        when(studentService.deactivate(99L)).thenThrow(new StudentNotFoundException(99L));

        mockMvc.perform(post("/students/99/deactivate").with(csrf()))
                .andExpect(status().isNotFound());

        verify(studentService).deactivate(99L);
    }

    @Test
    void activateUnknownIdIs404() throws Exception {
        when(studentService.activate(99L)).thenThrow(new StudentNotFoundException(99L));

        mockMvc.perform(post("/students/99/activate").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonNumericIdIsRejectedWithoutTouchingService() throws Exception {
        // "abc" never reaches the service: MVC's type conversion answers 400 before the lookup.
        mockMvc.perform(post("/students/abc/deactivate").with(csrf()))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(studentService);
    }

    @Test
    void postDeactivateWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/students/1/deactivate"))
                .andExpect(status().isForbidden());

        verify(studentService, never()).deactivate(anyLong());
    }

    @Test
    void postActivateWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/students/1/activate"))
                .andExpect(status().isForbidden());

        verify(studentService, never()).activate(anyLong());
    }
}
