package com.example.usersapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherFixtures;
import com.example.usersapp.domain.TeacherRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class TeacherServiceTest {

    private static final Sort BY_NAME = Sort.by("fullName");

    @Mock
    private TeacherRepository teacherRepository;

    @Test
    void findAllSortedByName() {
        List<Teacher> all = List.of(
                TeacherFixtures.teacher("Иван Иванов", "ivanov@example.com"),
                TeacherFixtures.teacher("Пётр Петров", "petrov@example.com"));
        when(teacherRepository.findAll(BY_NAME)).thenReturn(all);

        assertThat(new TeacherService(teacherRepository).findAll()).isEqualTo(all);

        verify(teacherRepository).findAll(BY_NAME);
    }
}
