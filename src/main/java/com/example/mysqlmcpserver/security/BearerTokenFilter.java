package com.example.mysqlmcpserver.security;

import com.example.mysqlmcpserver.config.McpServerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public final class BearerTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final McpServerProperties properties;
    private final byte[] expectedToken;

    public BearerTokenFilter(McpServerProperties properties) {
        this.properties = properties;
        this.expectedToken = properties.authToken().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String requestPath = contextPath == null || contextPath.isEmpty()
                ? requestUri
                : requestUri.substring(contextPath.length());
        return !properties.endpointPath().equals(requestPath);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!hasValidToken(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasValidToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] actualToken = authorization.substring(BEARER_PREFIX.length())
                .getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actualToken, expectedToken);
    }
}
