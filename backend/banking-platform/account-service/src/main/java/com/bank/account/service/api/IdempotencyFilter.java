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
        // We do not require the header, but if present, we enforce it
        if (key == null || key.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Cache the request body so we can hash it before the controller stream gets consumed
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        byte[] bodyBytes = cachedRequest.getCachedBody();
        String requestHash = hashBody(request.getMethod(), request.getRequestURI(), bodyBytes);

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
        filterChain.doFilter(cachedRequest, wrappedResponse);
        String responseBody = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
        idempotencyService.complete(key, wrappedResponse.getStatus(), responseBody);
        wrappedResponse.copyBodyToResponse();
    }

    private String hashBody(String method, String uri, byte[] bodyBytes) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(method.getBytes(StandardCharsets.UTF_8));
            digest.update(uri.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(bodyBytes);
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static class CachedBodyHttpServletRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            java.io.InputStream requestInputStream = request.getInputStream();
            this.cachedBody = org.springframework.util.StreamUtils.copyToByteArray(requestInputStream);
        }

        public byte[] getCachedBody() {
            return this.cachedBody;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() throws IOException {
            return new CachedBodyServletInputStream(this.cachedBody);
        }

        @Override
        public java.io.BufferedReader getReader() throws IOException {
            java.io.ByteArrayInputStream byteArrayInputStream = new java.io.ByteArrayInputStream(this.cachedBody);
            return new java.io.BufferedReader(new java.io.InputStreamReader(byteArrayInputStream));
        }
    }

    private static class CachedBodyServletInputStream extends jakarta.servlet.ServletInputStream {
        private final java.io.InputStream cachedBodyInputStream;

        public CachedBodyServletInputStream(byte[] cachedBody) {
            this.cachedBodyInputStream = new java.io.ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            try {
                return cachedBodyInputStream.available() == 0;
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(jakarta.servlet.ReadListener readListener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() throws IOException {
            return cachedBodyInputStream.read();
        }
    }
}
