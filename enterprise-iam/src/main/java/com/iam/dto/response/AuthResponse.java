package com.iam.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType; // "Bearer"
    private long expiresIn;    // seconds
    private boolean mfaRequired;
    private String mfaToken;   // temp token if MFA required
    private UserResponse user;
}
