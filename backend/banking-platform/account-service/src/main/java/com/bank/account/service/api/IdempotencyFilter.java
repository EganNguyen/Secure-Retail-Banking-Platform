package com.bank.account.service.api;

import com.bank.transfer.infrastructure.idempotency.IdempotencyRecordEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    private final IdempotencyService idempotencyService;

    public IdempotencyFilter(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().startsWith("/api/v1/transfers");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader(IDEMPOTENCY_HEADER);
        if (key == null || key.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing X-Idempotency-Key header");
            return;
        }

        String requestHash = request.getMethod() + ":" + request.getRequestURI();
        IdempotencyRecordEntity existing = idempotencyService.find(key).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "Idempotency key reused with a different request");
                return;
            }
            if (!existing.isCompleted()) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "Transfer request is already in progress");
                return;
            }
            response.setStatus(existing.getStatusCode());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(existing.getResponseBody());
            return;
        }

        idempotencyService.begin(key, requestHash, request.getMethod(), request.getRequestURI());
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrappedResponse);
        String responseBody = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
        idempotencyService.complete(key, wrappedResponse.getStatus(), responseBody);
        wrappedResponse.copyBodyToResponse();
    }
}
