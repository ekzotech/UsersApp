package com.example.usersapp.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeacherForm {

    @NotBlank(message = "Укажите ФИО")
    @Size(min = 2, max = 100, message = "ФИО: от 2 до 100 символов")
    private String fullName;

    @NotBlank(message = "Укажите email")
    @Email(message = "Некорректный email")
    @Size(max = 254, message = "Email: не более 254 символов")
    private String email;

    private boolean curator;

    @NotBlank(message = "Укажите логин")
    @Size(min = 3, max = 64, message = "Логин: от 3 до 64 символов")
    private String username;

    @NotBlank(message = "Укажите пароль")
    @Size(min = 8, message = "Пароль: минимум 8 символов")
    private String password;
}
