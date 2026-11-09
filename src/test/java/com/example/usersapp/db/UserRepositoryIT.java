package com.example.usersapp.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.usersapp.domain.User;
import com.example.usersapp.domain.UserRepository;
import com.example.usersapp.domain.UserRole;
import com.example.usersapp.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class UserRepositoryIT extends PostgresTestBase {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUsernameReturnsSavedUser() {
        userRepository.save(new User("alice", "{bcrypt}$2a$hash", UserRole.ADMIN));

        assertThat(userRepository.findByUsername("alice")).hasValueSatisfying(user -> {
            assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(user.getPassword()).isEqualTo("{bcrypt}$2a$hash");
        });
    }

    @Test
    void findByUsernameIsCaseSensitive() {
        userRepository.save(new User("alice", "{bcrypt}$2a$hash", UserRole.ADMIN));

        assertThat(userRepository.findByUsername("Alice")).isEmpty();
    }

    @Test
    void existsByUsernameIgnoreCaseMatchesAnyCase() {
        userRepository.save(new User("alice", "{bcrypt}$2a$hash", UserRole.ADMIN));

        assertThat(userRepository.existsByUsernameIgnoreCase("ALICE")).isTrue();
    }

    @Test
    void rejectsDuplicateUsernameInDifferentCase() {
        userRepository.save(new User("alice", "{bcrypt}$2a$hash", UserRole.ADMIN));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("Alice", "{bcrypt}$2a$hash", UserRole.TEACHER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
