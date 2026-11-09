package com.example.usersapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.usersapp.domain.User;
import com.example.usersapp.domain.UserRepository;
import com.example.usersapp.domain.UserRole;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void loadsUserWithRoleAuthority() {
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(new User("admin", "{bcrypt}$2a$10$hash", UserRole.ADMIN)));

        UserDetails details = new CustomUserDetailsService(userRepository).loadUserByUsername("admin");

        assertThat(details.getUsername()).isEqualTo("admin");
        assertThat(details.getPassword()).isEqualTo("{bcrypt}$2a$10$hash");
        assertThat(details.getAuthorities())
                .singleElement()
                .satisfies(authority -> assertThat(authority.getAuthority()).isEqualTo("ROLE_ADMIN"));
    }

    @Test
    void loadsTeacherAuthority() {
        when(userRepository.findByUsername("teacher"))
                .thenReturn(Optional.of(new User("teacher", "{bcrypt}$2a$10$hash", UserRole.TEACHER)));

        UserDetails details = new CustomUserDetailsService(userRepository).loadUserByUsername("teacher");

        assertThat(details.getUsername()).isEqualTo("teacher");
        assertThat(details.getAuthorities())
                .singleElement()
                .satisfies(authority -> assertThat(authority.getAuthority()).isEqualTo("ROLE_TEACHER"));
    }

    @Test
    void unknownUsernameThrows() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new CustomUserDetailsService(userRepository).loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void lookupUsesExactUsername() {
        when(userRepository.findByUsername("Admin"))
                .thenReturn(Optional.of(new User("admin", "{bcrypt}$2a$10$hash", UserRole.ADMIN)));

        UserDetails details = new CustomUserDetailsService(userRepository).loadUserByUsername("Admin");

        verify(userRepository).findByUsername("Admin");
        assertThat(details.getUsername()).isEqualTo("admin");
    }
}
