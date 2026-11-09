package com.example.usersapp.domain;

/**
 * Fixture factory for {@link Teacher} in tests: the entity's no-arg constructor is protected
 * (JPA convention), so tests in other packages build instances through this class.
 */
public final class TeacherFixtures {

    private TeacherFixtures() {
    }

    public static Teacher teacher(String fullName, String email) {
        Teacher teacher = new Teacher();
        teacher.setFullName(fullName);
        teacher.setEmail(email);
        return teacher;
    }
}
