package com.example.usersapp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class UserRoleTest {

    @Test
    void adminUserHasRoleAdminAuthority() {
        assertThat(new User("a", "x", UserRole.ADMIN).getAuthorities())
                .singleElement()
                .extracting(GrantedAuthority::getAuthority)
                .isEqualTo("ROLE_ADMIN");
    }

    @Test
    void teacherUserHasRoleTeacherAuthority() {
        assertThat(new User("t", "x", UserRole.TEACHER).getAuthorities())
                .singleElement()
                .extracting(GrantedAuthority::getAuthority)
                .isEqualTo("ROLE_TEACHER");
    }
}
