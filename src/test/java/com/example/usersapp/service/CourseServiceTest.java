package com.example.usersapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.usersapp.domain.Course;
import com.example.usersapp.domain.CourseFixtures;
import com.example.usersapp.domain.CourseRepository;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherFixtures;
import com.example.usersapp.domain.TeacherRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    private static final Sort BY_TITLE = Sort.by("title");

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @InjectMocks
    private CourseService service;

    @Test
    void findAllSortedByTitle() {
        List<Course> all = List.of(
                CourseFixtures.course("mathematics", "Математика"),
                CourseFixtures.course("physics", "Физика"));
        when(courseRepository.findAll(BY_TITLE)).thenReturn(all);

        assertThat(service.findAll()).isEqualTo(all);

        verify(courseRepository).findAll(BY_TITLE);
    }

    @Test
    void curatorsReturnsActiveCurators() {
        List<Teacher> curators = List.of(TeacherFixtures.teacher("Пётр Кураторов", "curator@example.com"));
        when(teacherRepository.findByCuratorTrueAndActiveTrueOrderByFullName()).thenReturn(curators);

        assertThat(service.curators()).isEqualTo(curators);

        verify(teacherRepository).findByCuratorTrueAndActiveTrueOrderByFullName();
    }
}
