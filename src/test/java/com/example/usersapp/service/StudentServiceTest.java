package com.example.usersapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.StudentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    private static final Sort BY_NAME = Sort.by("fullName");

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
}
