package com.iam.exception;

import lombok.Getter;

@Getter
public class MfaRequiredException extends RuntimeException {
    private final String mfaToken;

    public MfaRequiredException(String message, String mfaToken) {
        super(message);
        this.mfaToken = mfaToken;
    }
}
