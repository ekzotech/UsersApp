package com.example.usersapp.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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

import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherFixtures;
import com.example.usersapp.service.AccessDeniedForCuratorException;
import com.example.usersapp.service.CourseService;
import com.example.usersapp.service.CuratorRuleException;
import com.example.usersapp.service.CuratorService;
import com.example.usersapp.service.StudentNotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.FlashMap;

@WebMvcTest(CuratorController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "curator1", roles = "TEACHER")
class CuratorControllerTest {

    private static final String CURATOR_USERNAME = "curator1";
    private static final String CSRF_PARAM = "_csrf";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void defaultUnassignedCandidates() {
        lenient().when(curatorService.unassignedCandidates())
                .thenReturn(new CuratorService.UnassignedCandidates(List.of(), 0));
    }

    @MockitoBean
    private CuratorService curatorService;

    @MockitoBean
    private CourseService courseService;

    @Test
    void curatorStudentsPageRendersOwnStudents() throws Exception {
        Teacher current = curator(7L, "Пётр Кураторов", "curator@example.com");
        Teacher other = curator(8L, "Ирина Другая", "other@example.com");
        Student own = student(5L, "Анна Смирнова", "anna@example.com");
        own.setCurator(current);
        Student unassigned = student(6L, "Дина Безкураторова", "dina@example.com");
        when(curatorService.studentsOfCurator(CURATOR_USERNAME)).thenReturn(List.of(own, unassigned));
        when(courseService.curators()).thenReturn(List.of(current, other));

        mockMvc.perform(get("/curator/students"))
                .andExpect(status().isOk())
                .andExpect(view().name("curator/students"))
                .andExpect(model().attribute("students", List.of(own, unassigned)))
                .andExpect(model().attribute("curators", List.of(current, other)))
                .andExpect(content().string(containsString("Мои студенты (2)")))
                .andExpect(content().string(containsString("Анна Смирнова")))
                .andExpect(content().string(containsString("Дина Безкураторова")))
                .andExpect(content().string(containsString("<select id=\"curatorId-5\" name=\"curatorId\">")))
                .andExpect(content().string(containsString("— без куратора —")))
                .andExpect(content().string(containsString("action=\"/curator/students/5/curator\"")))
                .andExpect(content().string(containsString("action=\"/curator/students/6/curator\"")))
                .andExpect(content().string(containsString("Сохранить")))
                .andExpect(content().string(containsString("Снять куратора")));

        verify(curatorService).studentsOfCurator(CURATOR_USERNAME);
    }

    @Test
    void curatorStudentsPageSelectsCurrentCurator() throws Exception {
        Teacher current = curator(7L, "Пётр Кураторов", "curator@example.com");
        Teacher other = curator(8L, "Ирина Другая", "other@example.com");
        Student own = student(5L, "Анна Смирнова", "anna@example.com");
        own.setCurator(current);
        when(curatorService.studentsOfCurator(CURATOR_USERNAME)).thenReturn(List.of(own));
        when(courseService.curators()).thenReturn(List.of(current, other));

        MvcResult result = mockMvc.perform(get("/curator/students"))
                .andExpect(status().isOk())
                .andReturn();

        String select = selectOf(result.getResponse().getContentAsString());
        assertThat(select.replaceAll("\\s+", " "))
                .contains("<option value=\"7\" selected=\"selected\">Пётр Кураторов</option>");
        assertThat(select.replaceAll("\\s+", " ")).contains("<option value=\"8\">Ирина Другая</option>");
    }

    @Test
    void curatorPageRemovalIsASeparateFormWithoutCuratorId() throws Exception {
        Student assigned = student(5L, "Анна Смирнова", "anna@example.com");
        assigned.setCurator(curator(7L, "Пётр Кураторов", "petr@example.com"));
        when(curatorService.studentsOfCurator(CURATOR_USERNAME))
                .thenReturn(List.of(assigned));
        when(courseService.curators()).thenReturn(List.of());

        MvcResult result = mockMvc.perform(get("/curator/students"))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();
        // The removal form posts to its own endpoint and carries no curatorId, so a browser can
        // never submit a removal as an assignment of the curator selected in the assign form.
        int remove = html.indexOf("action=\"/curator/students/5/remove\"");
        assertThat(remove).isPositive();
        int close = html.indexOf("</form>", remove);
        assertThat(close).isPositive();
        String removeForm = html.substring(remove, close);
        assertThat(removeForm).contains(">Снять куратора</button>");
        assertThat(removeForm).doesNotContain("name=\"curatorId\"");
        assertThat(html).contains("action=\"/curator/students/5/curator\"");
        assertThat(html).contains("name=\"_csrf\"");
    }

    @Test
    void claimRedirectsToTheCuratorPageWithFlash() throws Exception {
        mockMvc.perform(post("/curator/students/5/claim").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/curator/students"))
                .andExpect(flash().attribute("success", "Куратор назначен"));
        verify(curatorService).claimCurator(CURATOR_USERNAME, 5L);
    }

    @Test
    void removeRedirectsToTheCuratorPageWithFlash() throws Exception {
        mockMvc.perform(post("/curator/students/5/remove").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/curator/students"))
                .andExpect(flash().attribute("success", "Куратор снят"));
        verify(curatorService).removeCurator(5L);
    }

    @Test
    void assignRedirectsWithFlash() throws Exception {
        mockMvc.perform(post("/curator/students/5/curator").param("curatorId", "7").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/curator/students"))
                .andExpect(flash().attribute("success", "Куратор назначен"));

        verify(curatorService).assertCuratorOwnsStudent(CURATOR_USERNAME, 5L);
        verify(curatorService).assignCurator(5L, 7L);
        verify(curatorService, never()).removeCurator(anyLong());
    }

    @Test
    void assignEmptyCuratorIdRemovesCurator() throws Exception {
        mockMvc.perform(post("/curator/students/5/curator").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/curator/students"))
                .andExpect(flash().attribute("success", "Куратор снят"));

        verify(curatorService).assertCuratorOwnsStudent(CURATOR_USERNAME, 5L);
        verify(curatorService).removeCurator(5L);
        verify(curatorService, never()).assignCurator(anyLong(), anyLong());
    }

    @Test
    void assignBlankCuratorIdRemovesCurator() throws Exception {
        mockMvc.perform(post("/curator/students/5/curator").param("curatorId", "").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/curator/students"));

        verify(curatorService).removeCurator(5L);
        verify(curatorService, never()).assignCurator(anyLong(), anyLong());
    }

    @Test
    void assignForeignStudentIs403() throws Exception {
        doThrow(new AccessDeniedForCuratorException())
                .when(curatorService).assertCuratorOwnsStudent(CURATOR_USERNAME, 5L);

        mockMvc.perform(post("/curator/students/5/curator").param("curatorId", "7").with(csrf()))
                .andExpect(status().isForbidden());

        verify(curatorService, never()).assignCurator(anyLong(), anyLong());
        verify(curatorService, never()).removeCurator(anyLong());
    }

    @Test
    void assignRuleViolationShowsFlashError() throws Exception {
        doThrow(new CuratorRuleException("Нельзя назначить уволенного преподавателя"))
                .when(curatorService).assignCurator(5L, 7L);

        mockMvc.perform(post("/curator/students/5/curator").param("curatorId", "7").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/curator/students"))
                .andExpect(flash().attribute("error", "Нельзя назначить уволенного преподавателя"));
    }

    @Test
    void assignUnknownStudentIs404() throws Exception {
        doThrow(new StudentNotFoundException(99L))
                .when(curatorService).assertCuratorOwnsStudent(CURATOR_USERNAME, 99L);

        mockMvc.perform(post("/curator/students/99/curator").param("curatorId", "7").with(csrf()))
                .andExpect(status().isNotFound());

        verify(curatorService, never()).assignCurator(anyLong(), anyLong());
    }

    @Test
    void nonCuratorTeacherGets403() throws Exception {
        when(curatorService.studentsOfCurator(CURATOR_USERNAME)).thenThrow(new AccessDeniedForCuratorException());

        mockMvc.perform(get("/curator/students"))
                .andExpect(status().isForbidden());

        verify(curatorService).studentsOfCurator(CURATOR_USERNAME);
    }

    @Test
    void nonCuratorTeacherOnAssignIs403BeforeMutation() throws Exception {
        // A non-curator account reaches the URL (ROLE_TEACHER) but the ownership check throws first.
        doThrow(new AccessDeniedForCuratorException())
                .when(curatorService).assertCuratorOwnsStudent(CURATOR_USERNAME, 5L);

        mockMvc.perform(post("/curator/students/5/curator").param("curatorId", "7").with(csrf()))
                .andExpect(status().isForbidden());

        verify(curatorService, never()).assignCurator(anyLong(), anyLong());
        verify(curatorService, never()).removeCurator(anyLong());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminForbiddenOnCuratorPage() throws Exception {
        mockMvc.perform(get("/curator/students")).andExpect(status().isForbidden());

        mockMvc.perform(post("/curator/students/5/curator").param("curatorId", "7").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(curatorService, courseService);
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/curator/students/5/curator").param("curatorId", "7"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(curatorService, courseService);
    }

    @Test
    void curatorPageRendersRuleViolationFlashError() throws Exception {
        when(curatorService.studentsOfCurator(CURATOR_USERNAME)).thenReturn(List.of());
        when(courseService.curators()).thenReturn(List.of());
        doThrow(new CuratorRuleException("Нельзя назначить уволенного преподавателя"))
                .when(curatorService).assignCurator(5L, 7L);

        MvcResult redirect = mockMvc.perform(post("/curator/students/5/curator")
                        .param("curatorId", "7")
                        .with(csrf()))
                .andReturn();
        FlashMap flash = redirect.getFlashMap();

        mockMvc.perform(get("/curator/students").flashAttrs(flash))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Нельзя назначить уволенного преподавателя")))
                .andExpect(content().string(containsString("flash error")));
    }

    private static String selectOf(String html) {
        int start = html.indexOf("<select");
        return html.substring(start, html.indexOf("</select>", start));
    }

    private static Student student(Long id, String fullName, String email) {
        Student student = new Student(fullName, email, "ИС-11");
        student.setId(id);
        return student;
    }

    private static Teacher curator(Long id, String fullName, String email) {
        Teacher teacher = TeacherFixtures.teacher(fullName, email);
        teacher.setId(id);
        teacher.setCurator(true);
        return teacher;
    }

    @Test
    void assignPageListsUnassignedStudents() throws Exception {
        when(curatorService.studentsOfCurator(CURATOR_USERNAME)).thenReturn(List.of());
        when(courseService.curators()).thenReturn(List.of());
        when(curatorService.unassignedCandidates())
                .thenReturn(new CuratorService.UnassignedCandidates(List.of(), 0));

        mockMvc.perform(get("/curator/students"))
                .andExpect(model().attribute("unassigned", List.of()))
                .andExpect(content().string(containsString("Все студенты распределены")));
    }

    @Test
    void claimAssignsTheCurrentCurator() throws Exception {
        mockMvc.perform(post("/curator/students/5/claim").with(csrf()))
                .andExpect(redirectedUrl("/curator/students"))
                .andExpect(flash().attribute("success", "Куратор назначен"));

        verify(curatorService).claimCurator(CURATOR_USERNAME, 5L);
        verify(curatorService, never()).assignCurator(anyLong(), anyLong());
    }

    @Test
    void claimRuleViolationShowsFlashError() throws Exception {
        doThrow(new CuratorRuleException("У студента уже есть куратор"))
                .when(curatorService).claimCurator(CURATOR_USERNAME, 5L);

        mockMvc.perform(post("/curator/students/5/claim").with(csrf()))
                .andExpect(redirectedUrl("/curator/students"))
                .andExpect(flash().attribute("error", "У студента уже есть куратор"));
    }

    @Test
    void removeCuratorEndpointNoLongerDependsOnTheSelectedOption() throws Exception {
        mockMvc.perform(post("/curator/students/5/remove").with(csrf()))
                .andExpect(redirectedUrl("/curator/students"))
                .andExpect(flash().attribute("success", "Куратор снят"));

        verify(curatorService).assertCuratorOwnsStudent(CURATOR_USERNAME, 5L);
        verify(curatorService).removeCurator(5L);
        verify(curatorService, never()).assignCurator(anyLong(), anyLong());
    }

    @Test
    void claimWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/curator/students/5/claim"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(curatorService);
    }

    @Test
    void curatorPageSaysHowManyCandidatesAreHidden() throws Exception {
        List<Student> page = List.of(student(1L, "Вера Орлова", "vera@example.com"));
        when(curatorService.studentsOfCurator(CURATOR_USERNAME)).thenReturn(List.of());
        when(courseService.curators()).thenReturn(List.of());
        when(curatorService.unassignedCandidates())
                .thenReturn(new CuratorService.UnassignedCandidates(page, 210));

        mockMvc.perform(get("/curator/students"))
                .andExpect(content().string(containsString("Показаны первые 1 из 210.")));
    }
}
