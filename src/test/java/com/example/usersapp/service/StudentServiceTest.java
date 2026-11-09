package com.example.usersapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.StudentRepository;
import com.example.usersapp.web.StudentForm;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    private static final Sort BY_NAME = Sort.by("fullName");
    private static final String STUDENT_NAME = "Анна Смирнова";
    private static final String GROUP_NAME = "ИС-11";

    @Mock
    private StudentRepository studentRepository;

    @Test
    void blankQueryListsAllSortedByName() {
        List<Student> all = List.of(
                new Student("Анна Смирнова", "anna@example.com", "ИС-11"),
                new Student("Борис Волков", "boris@example.com", "ИС-12"));
        when(studentRepository.findAll(BY_NAME)).thenReturn(all);

        assertThat(new StudentService(studentRepository).findAll(null)).isEqualTo(all);

        verify(studentRepository).findAll(BY_NAME);
        verify(studentRepository, never())
                .findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(anyString(), anyString(), any());
    }

    @Test
    void whitespaceOnlyQueryListsAll() {
        List<Student> all = List.of(new Student("Анна Смирнова", "anna@example.com", "ИС-11"));
        when(studentRepository.findAll(BY_NAME)).thenReturn(all);

        assertThat(new StudentService(studentRepository).findAll("   ")).isEqualTo(all);

        verify(studentRepository).findAll(BY_NAME);
        verify(studentRepository, never())
                .findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(anyString(), anyString(), any());
    }

    @Test
    void searchTrimsQueryAndMatchesNameOrEmail() {
        List<Student> found = List.of(new Student("Анна Смирнова", "anna@example.com", "ИС-11"));
        when(studentRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase("анн", "анн", BY_NAME))
                .thenReturn(found);

        assertThat(new StudentService(studentRepository).findAll(" анн ")).isEqualTo(found);

        verify(studentRepository)
                .findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase("анн", "анн", BY_NAME);
        verify(studentRepository, never()).findAll(any(Sort.class));
    }

    @Test
    void createSavesActiveStudentWithTrimmedFields() {
        when(studentRepository.existsByEmailIgnoreCase("student@example.com")).thenReturn(false);
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Student created = new StudentService(studentRepository)
                .create(form("  " + STUDENT_NAME + " ", " student@example.com ", " " + GROUP_NAME + "  "));

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(captor.capture());
        Student saved = captor.getValue();
        assertThat(saved.getFullName()).isEqualTo(STUDENT_NAME);
        assertThat(saved.getEmail()).isEqualTo("student@example.com");
        assertThat(saved.getGroupName()).isEqualTo(GROUP_NAME);
        assertThat(saved.isActive()).isTrue();
        assertThat(created).isSameAs(saved);
    }

    @Test
    void createWithDuplicateEmailThrows() {
        when(studentRepository.existsByEmailIgnoreCase("S@X.COM")).thenReturn(true);

        assertThatThrownBy(() -> new StudentService(studentRepository)
                .create(form(STUDENT_NAME, "S@X.COM", GROUP_NAME)))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("S@X.COM");

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    void deactivateSetsInactive() {
        Student student = new Student(STUDENT_NAME, "student@example.com", GROUP_NAME);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Student deactivated = new StudentService(studentRepository).deactivate(1L);

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
        assertThat(deactivated).isSameAs(student);
    }

    @Test
    void activateSetsActive() {
        Student student = new Student(STUDENT_NAME, "student@example.com", GROUP_NAME);
        student.setActive(false);
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Student activated = new StudentService(studentRepository).activate(2L);

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(activated).isSameAs(student);
    }

    @Test
    void unknownIdThrows() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new StudentService(studentRepository).deactivate(99L))
                .isInstanceOf(StudentNotFoundException.class)
                .hasMessage("Студент не найден: 99");
        assertThatThrownBy(() -> new StudentService(studentRepository).activate(99L))
                .isInstanceOf(StudentNotFoundException.class)
                .hasMessage("Студент не найден: 99");
        verify(studentRepository, never()).save(any(Student.class));
    }

    private static StudentForm form(String fullName, String email, String groupName) {
        StudentForm form = new StudentForm();
        form.setFullName(fullName);
        form.setEmail(email);
        form.setGroupName(groupName);
        return form;
    }
}
