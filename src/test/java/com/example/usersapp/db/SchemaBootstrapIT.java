package com.example.usersapp.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.usersapp.support.PostgresTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class SchemaBootstrapIT extends PostgresTestBase {

    private static final List<String> COURSE_TITLES = List.of(
            "Математика", "Физика", "Химия", "Русский язык", "Английский язык", "Русская литература",
            "Английская литература", "История", "Философия", "Психология", "Социология", "Экономика");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliedBothMigrations() {
        assertThat(countSuccessfulMigrations()).isEqualTo(2);
    }

    private Integer countSuccessfulMigrations() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
    }

    @Test
    void courseCatalogueHasTwelveCoursesInOrder() {
        assertThat(jdbcTemplate.queryForList("SELECT title FROM courses ORDER BY id", String.class))
                .containsExactlyElementsOf(COURSE_TITLES);
    }

    @Test
    void studentCuratorForeignKeyPointsToTeachers() {
        Map<String, Object> fk = jdbcTemplate.queryForMap("""
                SELECT target.relname AS target_table, fk_col.attname AS target_column,
                       c.confdeltype::text AS delete_action
                FROM pg_constraint c
                JOIN pg_class source ON source.oid = c.conrelid
                JOIN pg_class target ON target.oid = c.confrelid
                JOIN pg_attribute fk_col ON fk_col.attrelid = c.confrelid
                                        AND fk_col.attnum = c.confkey[1]
                WHERE c.contype = 'f' AND source.relname = 'students' AND c.conname = 'students_curator_id_fkey'
                """);
        assertThat(fk.get("target_table")).isEqualTo("teachers");
        assertThat(fk.get("target_column")).isEqualTo("id");
        assertThat(fk.get("delete_action")).isEqualTo("n");
    }

    @Test
    void emailUniquenessIsCaseInsensitive() {
        jdbcTemplate.update("INSERT INTO students (full_name, email, group_name) VALUES (?, ?, ?)",
                "Иван", "Ivan@Example.COM", "ИС-11");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO students (full_name, email, group_name) VALUES (?, ?, ?)",
                "Пётр", "ivan@example.com", "ИС-12"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enrollmentUniquenessConstraint() {
        Long teacherId = insertTeacher("Куратор Проверочный", "enrollment-teacher@example.com");
        Long studentId = insertStudent("Студент Проверочный", "enrollment-student@example.com");
        Long courseId = jdbcTemplate.queryForObject("SELECT id FROM courses WHERE slug = 'math'", Long.class);

        jdbcTemplate.update("INSERT INTO enrollments (student_id, teacher_id, course_id) VALUES (?, ?, ?)",
                studentId, teacherId, courseId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO enrollments (student_id, teacher_id, course_id) VALUES (?, ?, ?)",
                studentId, teacherId, courseId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long insertTeacher(String fullName, String email) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO teachers (full_name, email) VALUES (?, ?) RETURNING id",
                Long.class, fullName, email);
    }

    private Long insertStudent(String fullName, String email) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO students (full_name, email, group_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, fullName, email, "ИТ-21");
    }
}
