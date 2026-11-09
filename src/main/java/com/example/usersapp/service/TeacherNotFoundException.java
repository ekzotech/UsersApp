package com.example.usersapp.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TeacherNotFoundException extends RuntimeException {

    public TeacherNotFoundException(Long id) {
        super("Преподаватель не найден: " + id);
    }
}
