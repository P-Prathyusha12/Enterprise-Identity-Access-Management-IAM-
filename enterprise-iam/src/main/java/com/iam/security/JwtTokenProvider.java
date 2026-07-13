package com.iam.security;

import com.iam.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final AppProperties appProperties;
    private final StringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String MFA_PENDING_PREFIX = "jwt:mfa:pending:";

    public String generateAccessToken(UserDetails userDetails, Map<String, Object> extraClaims) {
        long expiration = appProperties.getJwt().getAccessTokenExpiration();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .id(UUID.randomUUID().toString())
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(String subject) {
        long expiration = appProperties.getJwt().getRefreshTokenExpiration();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .id(UUID.randomUUID().toString())
                .signWith(getSigningKey())
                .compact();
    }

    public String generateMfaPendingToken(String email, long expiryMs) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(MFA_PENDING_PREFIX + token, email, expiryMs, TimeUnit.MILLISECONDS);
        return token;
    }

    public String getMfaPendingEmail(String mfaToken) {
        return redisTemplate.opsForValue().get(MFA_PENDING_PREFIX + mfaToken);
    }

    public void deleteMfaPendingToken(String mfaToken) {
        redisTemplate.delete(MFA_PENDING_PREFIX + mfaToken);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token) && !isTokenBlacklisted(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public void blacklistToken(String token, long expiryMs) {
        try {
            String jti = extractJti(token);
            if (jti != null && expiryMs > 0) {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "revoked", expiryMs, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.error("Failed to blacklist token", e);
        }
    }

    public boolean isTokenBlacklisted(String token) {
        try {
            String jti = extractJti(token);
            if (jti == null) {
                return false;
            }
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(appProperties.getJwt().getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public long getRemainingExpiryMs(String token) {
        try {
            Date expiry = extractExpiration(token);
            return Math.max(0, expiry.getTime() - System.currentTimeMillis());
        } catch (Exception e) {
            return 0;
        }
    }
}
