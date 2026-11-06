package com.example.mysqlmcpserver.security;

import com.example.mysqlmcpserver.config.McpServerProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class BearerTokenFilterTest {

    private final BearerTokenFilter filter =
            new BearerTokenFilter(new McpServerProperties("/mcp", "test-token"));

    @Test
    void skipsHealthEndpoint() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isInstanceOf(HttpServletRequest.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Basic dGVzdC10b2tlbg==", "Bearer wrong-token", "Bearer test-token ", "Bearer  test-token"})
    void rejectsMissingMalformedOrWrongToken(String authorization) throws Exception {
        var request = new MockHttpServletRequest("POST", "/mcp");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    void acceptsExactBearerToken() throws Exception {
        var request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isInstanceOf(HttpServletRequest.class);
    }
}
