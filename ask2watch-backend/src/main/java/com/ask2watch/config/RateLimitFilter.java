package com.ask2watch.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> registerBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = getClientIp(request);

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            if ("/api/auth/login".equals(path)) {
                Bucket bucket = loginBuckets.computeIfAbsent(ip, k -> createLoginBucket());
                if (!bucket.tryConsume(1)) {
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Too many login attempts. Try again later.\"}");
                    return;
                }
            } else if ("/api/auth/register".equals(path)) {
                Bucket bucket = registerBuckets.computeIfAbsent(ip, k -> createRegisterBucket());
                if (!bucket.tryConsume(1)) {
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Too many registration attempts. Try again later.\"}");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private Bucket createLoginBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(5, Duration.ofMinutes(1)))
                .build();
    }

    private Bucket createRegisterBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(3, Duration.ofHours(1)))
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
