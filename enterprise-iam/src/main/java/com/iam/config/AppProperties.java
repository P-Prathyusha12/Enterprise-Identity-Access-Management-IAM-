package com.iam.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Security security = new Security();
    private Mail mail = new Mail();

    @Data
    public static class Jwt {
        private String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        private long accessTokenExpiration = 900000L;      // 15 minutes in ms
        private long refreshTokenExpiration = 604800000L;  // 7 days in ms
        private String issuer = "enterprise-iam";
    }

    @Data
    public static class Security {
        private int maxFailedAttempts = 5;
        private int lockDurationMinutes = 30;
        private int emailTokenExpiryHours = 24;
        private int passwordResetTokenExpiryHours = 1;
        private int otpExpiryMinutes = 5;
    }

    @Data
    public static class Mail {
        private String from = "noreply@enterprise-iam.com";
        private String fromName = "Enterprise IAM";
        private String baseUrl = "http://localhost:8080";
    }
}
