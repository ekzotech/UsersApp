-- UsersApp schema. Owned by Flyway; Hibernate runs with ddl-auto=validate.

CREATE TABLE teachers (
    id         BIGSERIAL PRIMARY KEY,
    full_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(254) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    curator    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX teachers_email_lower_key ON teachers (lower(email));

CREATE TABLE students (
    id         BIGSERIAL PRIMARY KEY,
    full_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(254) NOT NULL,
    group_name VARCHAR(20)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    curator_id BIGINT       REFERENCES teachers (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX students_email_lower_key ON students (lower(email));
CREATE INDEX students_curator_id_idx ON students (curator_id);

CREATE TABLE courses (
    id    BIGSERIAL PRIMARY KEY,
    slug  VARCHAR(64)  NOT NULL UNIQUE,
    title VARCHAR(100) NOT NULL
);

CREATE TABLE teacher_courses (
    teacher_id BIGINT NOT NULL REFERENCES teachers (id) ON DELETE CASCADE,
    course_id  BIGINT NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    PRIMARY KEY (teacher_id, course_id)
);

CREATE TABLE enrollments (
    id         BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    teacher_id BIGINT NOT NULL REFERENCES teachers (id) ON DELETE CASCADE,
    course_id  BIGINT NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT enrollments_unique_assignment UNIQUE (student_id, teacher_id, course_id)
);

CREATE INDEX enrollments_teacher_course_idx ON enrollments (teacher_id, course_id);
CREATE INDEX enrollments_student_idx ON enrollments (student_id);

CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(64)  NOT NULL,
    password   VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    teacher_id BIGINT UNIQUE REFERENCES teachers (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX users_username_lower_key ON users (lower(username));
