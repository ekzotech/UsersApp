package com.example.usersapp.service;

public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String username) {
        super("Логин " + username + " уже занят");
    }
}
