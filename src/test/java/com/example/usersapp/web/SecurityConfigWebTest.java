package com.example.usersapp.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.usersapp.UsersAppApplication;
import com.example.usersapp.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = UsersAppApplication.class)
@AutoConfigureMockMvc
@Testcontainers
// This class declares its own static @Container: Testcontainers restarts it for the next test
// class while Spring would reuse the cached context pointing at the dead port (see IMPLEMENTATION.md).
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
// The seed profile is the default profile; only SeedIntegrationIT starts it, these contexts must not seed.
@TestPropertySource(properties = "app.seed.enabled=false")
class SecurityConfigWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void loginPageRendersCsrfToken() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void loginPageUsesLayoutAndLoginForm() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(content().string(containsString(
                        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")))
                .andExpect(content().string(containsString("href=\"/css/app.css\"")))
                .andExpect(content().string(containsString("action=\"/login\"")))
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")));
    }

    @Test
    void protectedUrlRedirectsAnonymousToLogin() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void cssIsPublic() throws Exception {
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
    }

    @Test
    void loginWithValidCredentialsRedirectsToUsers() throws Exception {
        stubAdminWithPassword("secret123");

        mockMvc.perform(formLogin("/login").user("admin").password("secret123"))
                .andExpect(authenticated().withUsername("admin"))
                .andExpect(redirectedUrl("/users"));
    }

    @Test
    void loginWithWrongPasswordRedirectsWithError() throws Exception {
        stubAdminWithPassword("secret123");

        mockMvc.perform(formLogin("/login").user("admin").password("nope"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @WithMockUser
    void logoutRedirectsToLoginLogout() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    @WithMockUser
    void logoutWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/logout")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void adminUrlsAreForbiddenForTeacher() throws Exception {
        mockMvc.perform(get("/admin/anything")).andExpect(status().isForbidden());
        mockMvc.perform(get("/students/new")).andExpect(status().isForbidden());
        mockMvc.perform(get("/teachers/new")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void usersUrlIsAllowedForTeacher() throws Exception {
        // /users stays open to every authenticated user (functional requirement: everything after login).
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/list"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void curatorUrlsAreForbiddenForAdmin() throws Exception {
        mockMvc.perform(get("/curator/x")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void curatorUrlsAreAllowedForTeacher() throws Exception {
        // No controller yet: authorization passes first, then MVC answers 404.
        mockMvc.perform(get("/curator/x")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rootRedirectsToUsers() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isFound()).andExpect(redirectedUrl("/users"));
    }

    private void stubAdminWithPassword(String rawPassword) {
        Mockito.when(userDetailsService.loadUserByUsername("admin"))
                .thenReturn(User.withUsername("admin")
                        .password("{bcrypt}" + new BCryptPasswordEncoder().encode(rawPassword))
                        .roles("ADMIN")
                        .build());
    }
}
