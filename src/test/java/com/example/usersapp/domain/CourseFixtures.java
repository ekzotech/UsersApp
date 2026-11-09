package com.example.usersapp.domain;

/**
 * Fixture factory for {@link Course} in tests: the entity's no-arg constructor is protected
 * (JPA convention), so tests in other packages build instances through this class.
 */
public final class CourseFixtures {

    private CourseFixtures() {
    }

    public static Course course(String slug, String title) {
        Course course = new Course();
        course.setSlug(slug);
        course.setTitle(title);
        return course;
    }
}
