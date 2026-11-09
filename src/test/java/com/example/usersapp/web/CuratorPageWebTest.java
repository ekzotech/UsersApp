package com.example.usersapp.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.usersapp.UsersAppApplication;
import com.example.usersapp.domain.Course;
import com.example.usersapp.domain.CourseFixtures;
import com.example.usersapp.domain.CourseRepository;
import com.example.usersapp.domain.EnrollmentRepository;
import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.StudentRepository;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherFixtures;
import com.example.usersapp.domain.TeacherRepository;
import com.example.usersapp.domain.User;
import com.example.usersapp.domain.UserRepository;
import com.example.usersapp.domain.UserRole;
import com.example.usersapp.service.EnrollmentService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Amendment (11): the curator page renders {@code student.curator.fullName} from detached entities
 * (open-in-view=false), so only a full-context render proves the service's in-transaction touch.
 */
@SpringBootTest(classes = UsersAppApplication.class)
@AutoConfigureMockMvc
@Testcontainers
// Its own static @Container: repeat the AFTER_CLASS option (see IMPLEMENTATION.md).
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CuratorPageWebTest {

    private static final String CURATOR_NAME = "Марина Курилова";
    private static final String STUDENT_NAME = "Вера Орлова";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private CourseRepository courseRepository;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @AfterEach
    void clearDatabase() {
        enrollmentRepository.deleteAll();
        userRepository.deleteAll();
        studentRepository.deleteAll();
        teacherRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "curator1", roles = "TEACHER")
    void curatorPageRendersStudentAndCuratorNames() throws Exception {
        Teacher curator = persistCurator("curator1", CURATOR_NAME);
        Student student = studentRepository.save(new Student(STUDENT_NAME, "vera@example.com", "ИС-14"));
        student.setCurator(curator);
        studentRepository.saveAndFlush(student);

        String html = mockMvc.perform(get("/curator/students"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(UTF_8);

        assertThat(html).contains(STUDENT_NAME);
        assertThat(html).as("the curator column renders student.curator.fullName").contains(CURATOR_NAME);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void teacherProfilePageRendersStudentAndCourseNames() throws Exception {
        // Same detached-entity render (open-in-view=false), on the ADMIN-only teacher profile.
        Course algebra = courseRepository.save(CourseFixtures.course("probe-" + suffix(), "Алгебра"));
        Teacher teacher = TeacherFixtures.teacher("Иван Предметников", "probe-" + suffix() + "@example.com");
        teacher.getCourses().add(algebra);
        Teacher saved = teacherRepository.saveAndFlush(teacher);
        Student student = studentRepository.saveAndFlush(
                new Student("Яна Зуева", "zoe-" + suffix() + "@example.com", "ИС-11"));
        enrollmentService.enroll(saved.getId(), algebra.getId(), student.getId());

        String html = mockMvc.perform(get("/teachers/" + saved.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(UTF_8);

        assertThat(html).contains("Яна Зуева");
        assertThat(html).as("enrollment rows render course.title outside the service transaction").contains("Алгебра");
        assertThat(html).contains("name=\"studentId\">");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void teacherProfileEnrollsFromTheAssignForm() throws Exception {
        Course physics = courseRepository.save(CourseFixtures.course("probe-" + suffix(), "Физика"));
        Teacher teacher = TeacherFixtures.teacher("Иван Предметников", "form-" + suffix() + "@example.com");
        teacher.getCourses().add(physics);
        Teacher saved = teacherRepository.saveAndFlush(teacher);
        Student student = studentRepository.saveAndFlush(
                new Student("Олег Формов", "oleg-" + suffix() + "@example.com", "ИС-12"));

        MvcResult result = mockMvc.perform(post("/teachers/" + saved.getId() + "/courses/" + physics.getId() + "/students")
                        .param("studentId", student.getId().toString())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/teachers/" + saved.getId());
        assertThat(enrollmentRepository.findByTeacherIdOrderByCourseTitleAscStudentFullNameAsc(saved.getId()))
                .extracting(enrollment -> enrollment.getStudent().getId())
                .containsExactly(student.getId());
    }

    private Teacher persistCurator(String username, String fullName) {
        Teacher curator = TeacherFixtures.teacher(fullName, "curator-" + suffix() + "@example.com");
        curator.setCurator(true);
        curator = teacherRepository.saveAndFlush(curator);
        User account = userRepository.save(new User(username, "{bcrypt}x", UserRole.TEACHER));
        account.setTeacher(curator);
        userRepository.saveAndFlush(account);
        return curator;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
