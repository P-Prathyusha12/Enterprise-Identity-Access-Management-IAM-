package com.iam.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iam.dto.response.ApiResponse;
import com.iam.util.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    private static final String LIMIT_KEY_PREFIX = "rate:limit:";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Apply rate limiting primarily on authentication endpoints
        if (path.startsWith("/api/v1/auth/")) {
            String clientIp = SecurityUtils.extractIpAddress(request);
            String redisKey = LIMIT_KEY_PREFIX + clientIp;

            Long requests = redisTemplate.opsForValue().increment(redisKey);
            if (requests != null && requests == 1) {
                redisTemplate.expire(redisKey, 1, TimeUnit.MINUTES);
            }

            if (requests != null && requests > MAX_REQUESTS_PER_MINUTE) {
                log.warn("Rate limit exceeded for IP: {}. Requests in current minute: {}", clientIp, requests);
                sendErrorResponse(response, clientIp);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, String ip) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(false)
                .message("Too many requests. Please try again in a minute.")
                .timestamp(LocalDateTime.now())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
