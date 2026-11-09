package com.example.usersapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherFixtures;
import com.example.usersapp.domain.TeacherRepository;
import com.example.usersapp.domain.User;
import com.example.usersapp.domain.UserRepository;
import com.example.usersapp.domain.UserRole;
import com.example.usersapp.web.TeacherForm;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class TeacherServiceTest {

    private static final Sort BY_NAME = Sort.by("fullName");
    private static final String FULL_NAME = "Иван Иванов";
    private static final String EMAIL = "ivanov@example.com";
    private static final String USERNAME = "ivanov";
    private static final String PASSWORD = "secret123";

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void findAllSortedByName() {
        List<Teacher> all = List.of(
                TeacherFixtures.teacher("Иван Иванов", "ivanov@example.com"),
                TeacherFixtures.teacher("Пётр Петров", "petrov@example.com"));
        when(teacherRepository.findAll(BY_NAME)).thenReturn(all);

        assertThat(service().findAll()).isEqualTo(all);

        verify(teacherRepository).findAll(BY_NAME);
    }

    @Test
    void createSavesTeacherAndAccount() {
        when(teacherRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase(USERNAME)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded");
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Teacher created = service().create(form(FULL_NAME, EMAIL, USERNAME, PASSWORD, true));

        ArgumentCaptor<Teacher> teacherCaptor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(teacherCaptor.capture());
        Teacher saved = teacherCaptor.getValue();
        assertThat(saved.getFullName()).isEqualTo(FULL_NAME);
        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(saved.isCurator()).isTrue();
        assertThat(saved.isActive()).isTrue();
        assertThat(created).isSameAs(saved);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User account = userCaptor.getValue();
        assertThat(account.getUsername()).isEqualTo(USERNAME);
        assertThat(account.getPassword()).isEqualTo("encoded");
        assertThat(account.getRole()).isEqualTo(UserRole.TEACHER);
        assertThat(account.getTeacher()).isSameAs(saved);
        verify(passwordEncoder).encode(PASSWORD);
    }

    @Test
    void createWithoutCuratorFlagSavesNonCurator() {
        when(teacherRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase(USERNAME)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded");
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().create(form(FULL_NAME, EMAIL, USERNAME, PASSWORD, false));

        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(captor.capture());
        assertThat(captor.getValue().isCurator()).isFalse();
    }

    @Test
    void createTrimsFields() {
        when(teacherRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase(USERNAME)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded");
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().create(form("  " + FULL_NAME + " ", " " + EMAIL + "  ", " " + USERNAME + " ", PASSWORD, false));

        ArgumentCaptor<Teacher> teacherCaptor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(teacherCaptor.capture());
        assertThat(teacherCaptor.getValue().getFullName()).isEqualTo(FULL_NAME);
        assertThat(teacherCaptor.getValue().getEmail()).isEqualTo(EMAIL);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo(USERNAME);
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(teacherRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service().create(form(FULL_NAME, EMAIL, USERNAME, PASSWORD, false)))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining(EMAIL);

        verify(teacherRepository, never()).save(any(Teacher.class));
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(teacherRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase(USERNAME)).thenReturn(true);

        assertThatThrownBy(() -> service().create(form(FULL_NAME, EMAIL, USERNAME, PASSWORD, false)))
                .isInstanceOf(DuplicateUsernameException.class)
                .hasMessageContaining(USERNAME);

        verify(teacherRepository, never()).save(any(Teacher.class));
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void deactivateSetsInactive() {
        Teacher teacher = TeacherFixtures.teacher(FULL_NAME, EMAIL);
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Teacher deactivated = service().deactivate(1L);

        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
        assertThat(captor.getValue().getDisplayStatus()).isEqualTo("Уволен");
        assertThat(deactivated).isSameAs(teacher);
    }

    @Test
    void activateSetsActive() {
        Teacher teacher = TeacherFixtures.teacher(FULL_NAME, EMAIL);
        teacher.setActive(false);
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(teacher));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Teacher activated = service().activate(2L);

        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(activated).isSameAs(teacher);
    }

    @Test
    void unknownTeacherThrows() {
        when(teacherRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deactivate(99L))
                .isInstanceOf(TeacherNotFoundException.class)
                .hasMessage("Преподаватель не найден: 99");
        assertThatThrownBy(() -> service().activate(99L))
                .isInstanceOf(TeacherNotFoundException.class)
                .hasMessage("Преподаватель не найден: 99");
        verify(teacherRepository, never()).save(any(Teacher.class));
    }

    @Test
    void accountIsSavedOnlyAfterTheTeacherExists() {
        when(teacherRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase(USERNAME)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded");
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().create(form(FULL_NAME, EMAIL, USERNAME, PASSWORD, true));

        InOrder order = Mockito.inOrder(teacherRepository, userRepository);
        order.verify(teacherRepository).save(any(Teacher.class));
        order.verify(userRepository).save(any(User.class));
    }

    private TeacherService service() {
        return new TeacherService(teacherRepository, userRepository, passwordEncoder);
    }

    private static TeacherForm form(String fullName, String email, String username, String password, boolean curator) {
        TeacherForm form = new TeacherForm();
        form.setFullName(fullName);
        form.setEmail(email);
        form.setUsername(username);
        form.setPassword(password);
        form.setCurator(curator);
        return form;
    }
}
