package com.exchange.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${rate-limit.requests-per-minute:100}")
    private int maxRequestsPerMinute;

    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String clientIp = getClientIp(request);
        int count = counters.computeIfAbsent(clientIp, k -> new AtomicInteger(0))
                .incrementAndGet();

        if (count > maxRequestsPerMinute) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"status":429,"error":"Too Many Requests","message":"Rate limit exceeded. Max %d requests/minute."}
                    """.formatted(maxRequestsPerMinute));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Scheduled(fixedDelay = 60_000)
    public void resetCounters() {
        counters.clear();
    }
}
