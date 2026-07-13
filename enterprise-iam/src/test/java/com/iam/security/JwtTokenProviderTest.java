package com.iam.security;

import com.iam.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtTokenProvider Unit Tests")
class JwtTokenProviderTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    private AppProperties.Jwt jwtProperties;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtProperties = new AppProperties.Jwt();
        // Use a valid base64-encoded 256-bit (32-byte) key
        jwtProperties.setSecret("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        jwtProperties.setAccessTokenExpiration(900000L);
        jwtProperties.setRefreshTokenExpiration(604800000L);

        when(appProperties.getJwt()).thenReturn(jwtProperties);

        userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("test@example.com")
                .password("hashedpassword")
                .authorities(List.of())
                .build();
    }

    @Test
    @DisplayName("GenerateAccessToken - should generate valid JWT token")
    void generateAccessToken_shouldReturnValidToken() {
        // When
        String token = jwtTokenProvider.generateAccessToken(userDetails, new HashMap<>());

        // Then
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("ExtractUsername - should correctly extract subject from token")
    void extractUsername_shouldReturnCorrectSubject() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(userDetails, new HashMap<>());

        // When
        String username = jwtTokenProvider.extractUsername(token);

        // Then
        assertThat(username).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("IsTokenValid - should return true for valid, non-expired token")
    void isTokenValid_shouldReturnTrue_forValidToken() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(userDetails, new HashMap<>());
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // When
        boolean isValid = jwtTokenProvider.isTokenValid(token, userDetails);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("IsTokenExpired - should return false for freshly generated token")
    void isTokenExpired_shouldReturnFalse_forFreshToken() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(userDetails, new HashMap<>());

        // When
        boolean isExpired = jwtTokenProvider.isTokenExpired(token);

        // Then
        assertThat(isExpired).isFalse();
    }

    @Test
    @DisplayName("BlacklistToken - should blacklist token in Redis")
    void blacklistToken_shouldAddToRedis() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(userDetails, new HashMap<>());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        jwtTokenProvider.blacklistToken(token, 900000L);

        // Then
        verify(valueOperations).set(anyString(), eq("revoked"), anyLong(), any());
    }

    @Test
    @DisplayName("IsTokenBlacklisted - should return true for blacklisted token")
    void isTokenBlacklisted_shouldReturnTrue_forBlacklistedToken() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(userDetails, new HashMap<>());
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        // When
        boolean isBlacklisted = jwtTokenProvider.isTokenBlacklisted(token);

        // Then
        assertThat(isBlacklisted).isTrue();
    }
}
