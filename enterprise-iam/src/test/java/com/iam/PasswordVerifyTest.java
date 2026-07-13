package com.iam;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordVerifyTest {

    @Test
    public void testPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = "$2a$12$LBHyRkJLRBV3OBcLo6JWBuRj3GhkFf4.VJO7MCv4pHZYYSQH9u3yO";
        boolean match = encoder.matches("Admin@123", hash);
        System.out.println("========== PASSWORD MATCH TEST ==========");
        System.out.println("Password matches: " + match);
        System.out.println("=========================================");
    }
}
