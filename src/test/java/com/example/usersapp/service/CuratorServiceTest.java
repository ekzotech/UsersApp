package com.example.usersapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.usersapp.domain.Student;
import com.example.usersapp.domain.StudentRepository;
import com.example.usersapp.domain.Teacher;
import com.example.usersapp.domain.TeacherFixtures;
import com.example.usersapp.domain.TeacherRepository;
import com.example.usersapp.domain.User;
import com.example.usersapp.domain.UserRepository;
import com.example.usersapp.domain.UserRole;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CuratorServiceTest {

    private static final String CURATOR_USERNAME = "curator1";

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CuratorService service;

    @Test
    void assignCuratorSetsCurator() {
        Student student = student(1L, "Анна Смирнова", "anna@example.com");
        Teacher teacher = curator(7L, "Пётр Кураторов", "curator@example.com");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(teacher));
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Student assigned = service.assignCurator(1L, 7L);

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(captor.capture());
        assertThat(captor.getValue().getCurator()).isSameAs(teacher);
        assertThat(assigned).isSameAs(student);
    }

    @Test
    void assignNonCuratorTeacherThrows() {
        Teacher teacher = teacher(7L, "Иван Предметников", "ivan@example.com");
        teacher.setCurator(false);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student(1L, "Анна Смирнова", "anna@example.com")));
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> service.assignCurator(1L, 7L))
                .isInstanceOf(CuratorRuleException.class)
                .hasMessageContaining("Куратор");

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    void assignInactiveTeacherThrows() {
        Teacher teacher = curator(7L, "Пётр Кураторов", "curator@example.com");
        teacher.setActive(false);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student(1L, "Анна Смирнова", "anna@example.com")));
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> service.assignCurator(1L, 7L))
                .isInstanceOf(CuratorRuleException.class)
                .hasMessageContaining("уволен");

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    void assignInactiveStudentThrows() {
        Student student = student(1L, "Анна Смирнова", "anna@example.com");
        student.setActive(false);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(curator(7L, "Пётр Кураторов", "curator@example.com")));

        assertThatThrownBy(() -> service.assignCurator(1L, 7L))
                .isInstanceOf(CuratorRuleException.class)
                .hasMessageContaining("отчислен");

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    void assignUnknownStudentThrows() {
        when(studentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignCurator(1L, 7L))
                .isInstanceOf(StudentNotFoundException.class);

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    void assignUnknownTeacherThrows() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student(1L, "Анна Смирнова", "anna@example.com")));
        when(teacherRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignCurator(1L, 7L))
                .isInstanceOf(TeacherNotFoundException.class);

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    void removeCuratorClearsCurator() {
        Student student = student(1L, "Анна Смирнова", "anna@example.com");
        student.setCurator(curator(7L, "Пётр Кураторов", "curator@example.com"));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Student removed = service.removeCurator(1L);

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(captor.capture());
        assertThat(captor.getValue().getCurator()).isNull();
        assertThat(removed).isSameAs(student);
    }

    @Test
    void studentsOfCuratorListsOwnStudents() {
        Teacher curator = curator(7L, "Пётр Кураторов", "curator@example.com");
        List<Student> own = List.of(student(1L, "Анна Смирнова", "anna@example.com"));
        when(userRepository.findByUsername(CURATOR_USERNAME)).thenReturn(Optional.of(account(curator)));
        when(studentRepository.findByCuratorIdOrderByFullName(7L)).thenReturn(own);

        assertThat(service.studentsOfCurator(CURATOR_USERNAME)).isEqualTo(own);

        verify(studentRepository).findByCuratorIdOrderByFullName(7L);
    }

    @Test
    void studentsOfCuratorForAccountWithoutTeacherIs403() {
        when(userRepository.findByUsername(CURATOR_USERNAME))
                .thenReturn(Optional.of(new User(CURATOR_USERNAME, "encoded", UserRole.ADMIN)));

        assertThatThrownBy(() -> service.studentsOfCurator(CURATOR_USERNAME))
                .isInstanceOf(AccessDeniedForCuratorException.class);

        verify(studentRepository, never()).findByCuratorIdOrderByFullName(any());
    }

    @Test
    void studentsOfCuratorForNonCuratorTeacherIs403() {
        Teacher lecturer = teacher(7L, "Иван Предметников", "ivan@example.com");
        when(userRepository.findByUsername(CURATOR_USERNAME)).thenReturn(Optional.of(account(lecturer)));

        assertThatThrownBy(() -> service.studentsOfCurator(CURATOR_USERNAME))
                .isInstanceOf(AccessDeniedForCuratorException.class);

        verify(studentRepository, never()).findByCuratorIdOrderByFullName(any());
    }

    @Test
    void studentsOfCuratorForDismissedCuratorIs403() {
        Teacher dismissed = curator(7L, "Пётр Кураторов", "curator@example.com");
        dismissed.setActive(false);
        when(userRepository.findByUsername(CURATOR_USERNAME)).thenReturn(Optional.of(account(dismissed)));

        assertThatThrownBy(() -> service.studentsOfCurator(CURATOR_USERNAME))
                .isInstanceOf(AccessDeniedForCuratorException.class);

        verify(studentRepository, never()).findByCuratorIdOrderByFullName(any());
    }

    @Test
    void assertCuratorOwnsStudentRejectsNonCuratorTeacher() {
        Teacher lecturer = teacher(7L, "Иван Предметников", "ivan@example.com");
        Student own = student(1L, "Анна Смирнова", "anna@example.com");
        own.setCurator(lecturer);
        when(userRepository.findByUsername(CURATOR_USERNAME)).thenReturn(Optional.of(account(lecturer)));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(own));

        assertThatThrownBy(() -> service.assertCuratorOwnsStudent(CURATOR_USERNAME, 1L))
                .isInstanceOf(AccessDeniedForCuratorException.class);
    }

    @Test
    void assertCuratorOwnsStudentRejectsDismissedCurator() {
        Teacher dismissed = curator(7L, "Пётр Кураторов", "curator@example.com");
        dismissed.setActive(false);
        Student own = student(1L, "Анна Смирнова", "anna@example.com");
        own.setCurator(dismissed);
        when(userRepository.findByUsername(CURATOR_USERNAME)).thenReturn(Optional.of(account(dismissed)));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(own));

        assertThatThrownBy(() -> service.assertCuratorOwnsStudent(CURATOR_USERNAME, 1L))
                .isInstanceOf(AccessDeniedForCuratorException.class);
    }

    @Test
    void assertCuratorOwnsStudentRejectsForeignStudent() {
        Teacher curator = curator(7L, "Пётр Кураторов", "curator@example.com");
        Student foreign = student(1L, "Глеб Иной", "gleb@example.com");
        foreign.setCurator(curator(8L, "Ирина Другая", "other@example.com"));
        when(userRepository.findByUsername(CURATOR_USERNAME)).thenReturn(Optional.of(account(curator)));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.assertCuratorOwnsStudent(CURATOR_USERNAME, 1L))
                .isInstanceOf(AccessDeniedForCuratorException.class);
    }

    @Test
    void assertCuratorOwnsStudentAcceptsOwnStudent() {
        Teacher curator = curator(7L, "Пётр Кураторов", "curator@example.com");
        Student own = student(1L, "Анна Смирнова", "anna@example.com");
        own.setCurator(curator);
        when(userRepository.findByUsername(CURATOR_USERNAME)).thenReturn(Optional.of(account(curator)));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(own));

        assertThatCode(() -> service.assertCuratorOwnsStudent(CURATOR_USERNAME, 1L)).doesNotThrowAnyException();
    }

    @Test
    void assertCuratorOwnsStudentRejectsUnassignedStudent() {
        when(userRepository.findByUsername(CURATOR_USERNAME))
                .thenReturn(Optional.of(account(curator(7L, "Пётр Кураторов", "curator@example.com"))));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student(1L, "Дина Безкураторова", "dina@example.com")));

        assertThatThrownBy(() -> service.assertCuratorOwnsStudent(CURATOR_USERNAME, 1L))
                .isInstanceOf(AccessDeniedForCuratorException.class);
    }

    private static Student student(Long id, String fullName, String email) {
        Student student = new Student(fullName, email, "ИС-11");
        student.setId(id);
        return student;
    }

    private static Teacher teacher(Long id, String fullName, String email) {
        Teacher teacher = TeacherFixtures.teacher(fullName, email);
        teacher.setId(id);
        return teacher;
    }

    private static Teacher curator(Long id, String fullName, String email) {
        Teacher teacher = teacher(id, fullName, email);
        teacher.setCurator(true);
        return teacher;
    }

    private static User account(Teacher teacher) {
        User user = new User(CURATOR_USERNAME, "encoded", UserRole.TEACHER);
        user.setTeacher(teacher);
        return user;
    }

    @Test
    void claimSetsTheCurrentCurator() {
        Teacher curator = curator(7L, "Пётр Кураторов", "curator@example.com");
        Student unassigned = student(9L, "Вера Орлова", "vera@example.com");
        when(userRepository.findByUsername(CURATOR_USERNAME)).thenReturn(Optional.of(account(curator)));
        when(studentRepository.findById(9L)).thenReturn(Optional.of(unassigned));
        when(studentRepository.save(unassigned)).thenReturn(unassigned);

        service.claimCurator(CURATOR_USERNAME, 9L);

        assertThat(unassigned.getCurator()).isSameAs(curator);
        verify(studentRepository).save(unassigned);
    }

    @Test
    void claimRejectsAStudentWhoAlreadyHasACurator() {
        Teacher curator = curator(7L, "Пётр Кураторов", "curator@example.com");
        Teacher other = curator(8L, "Марина Курилова", "other@example.com");
        Student taken = student(9L, "Вера Орлова", "vera@example.com");
        taken.setCurator(other);
        when(userRepository.findByUsername(CURATOR_USERNAME)).thenReturn(Optional.of(account(curator)));
        when(studentRepository.findById(9L)).thenReturn(Optional.of(taken));

        assertThatThrownBy(() -> service.claimCurator(CURATOR_USERNAME, 9L))
                .isInstanceOf(CuratorRuleException.class)
                .hasMessage("У студента уже есть куратор");

        verify(studentRepository, never()).save(taken);
    }

    @Test
    void claimRejectsADismissedStudent() {
        Teacher curator = curator(7L, "Пётр Кураторов", "curator@example.com");
        Student dismissed = student(9L, "Вера Орлова", "vera@example.com");
        dismissed.setActive(false);
        when(userRepository.findByUsername(CURATOR_USERNAME)).thenReturn(Optional.of(account(curator)));
        when(studentRepository.findById(9L)).thenReturn(Optional.of(dismissed));

        assertThatThrownBy(() -> service.claimCurator(CURATOR_USERNAME, 9L))
                .isInstanceOf(CuratorRuleException.class)
                .hasMessage("Нельзя назначить куратора отчисленному студенту");
    }

    @Test
    void claimForNonAccountIs403() {
        when(userRepository.findByUsername(CURATOR_USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claimCurator(CURATOR_USERNAME, 9L))
                .isInstanceOf(AccessDeniedForCuratorException.class);

        verify(studentRepository, never()).findById(any());
    }

    @Test
    void unassignedStudentsListsTheFirstCandidatesOnly() {
        int total = CuratorService.UNASSIGNED_PAGE_SIZE * 2;
        List<Student> all = java.util.stream.IntStream.rangeClosed(1, total)
                .mapToObj(i -> student((long) i, "Студент " + i, "s" + i + "@example.com"))
                .toList();
        when(studentRepository.findByCuratorIsNullAndActiveTrueOrderByFullName()).thenReturn(all);

        CuratorService.UnassignedCandidates candidates = service.unassignedCandidates();

        assertThat(candidates.students()).hasSize(CuratorService.UNASSIGNED_PAGE_SIZE);
        assertThat(candidates.students().get(0).getFullName()).isEqualTo("Студент 1");
        assertThat(candidates.total()).isEqualTo(total);
    }

    @Test
    void unassignedCandidatesAreAllWhenFewerThanThePageSize() {
        when(studentRepository.findByCuratorIsNullAndActiveTrueOrderByFullName())
                .thenReturn(List.of(student(1L, "Вера Орлова", "vera@example.com")));

        CuratorService.UnassignedCandidates candidates = service.unassignedCandidates();

        assertThat(candidates.students()).extracting(Student::getFullName).containsExactly("Вера Орлова");
        assertThat(candidates.total()).isEqualTo(1);
    }
}
