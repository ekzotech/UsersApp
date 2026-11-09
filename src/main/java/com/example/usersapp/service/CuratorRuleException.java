package com.example.usersapp.service;

/**
 * User-facing business rule violation for curator assignment. The message is shown in the
 * controller's flash, so no HTTP status is attached here.
 */
public class CuratorRuleException extends RuntimeException {

    public CuratorRuleException(String message) {
        super(message);
    }
}
