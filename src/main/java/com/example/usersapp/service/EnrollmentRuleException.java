package com.example.usersapp.service;

/**
 * User-facing business rule violation for enrolling a student into a teacher's course. The message
 * is shown in the controller's flash, so no HTTP status is attached here.
 */
public class EnrollmentRuleException extends RuntimeException {

    public EnrollmentRuleException(String message) {
        super(message);
    }
}
