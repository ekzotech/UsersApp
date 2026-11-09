package com.example.usersapp.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudentForm {

    @NotBlank(message = "Укажите ФИО")
    @Size(min = 2, max = 100, message = "ФИО: от 2 до 100 символов")
    private String fullName;

    @NotBlank(message = "Укажите email")
    @Email(message = "Некорректный email")
    @Size(max = 254, message = "Email: не более 254 символов")
    private String email;

    @NotBlank(message = "Укажите группу")
    @Size(min = 2, max = 20, message = "Группа: от 2 до 20 символов")
    private String groupName;
}
