package com.iam.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a user account is locked due to too many failed login attempts.
 */
@ResponseStatus(HttpStatus.LOCKED)
public class AccountLockedException extends RuntimeException {

    public AccountLockedException(String message) {
        super(message);
    }

    public AccountLockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
