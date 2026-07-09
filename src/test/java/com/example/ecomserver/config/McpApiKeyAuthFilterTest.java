package com.example.ecomserver.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class McpApiKeyAuthFilterTest {

    private static final String KEY = "s3cr3t-key";

    private MockHttpServletResponse run(McpApiKeyAuthFilter filter, MockHttpServletRequest req)
            throws ServletException, IOException {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    private static boolean passedThrough(MockHttpServletResponse res) {
        // 200 is the mock default; the filter only ever sets 401 when it blocks.
        return res.getStatus() == 200;
    }

    @Test
    void disabledWhenNoKeyConfigured() throws Exception {
        var filter = new McpApiKeyAuthFilter("");
        var req = new MockHttpServletRequest("POST", "/mcp");
        assertThat(passedThrough(run(filter, req))).isTrue();
    }

    @Test
    void allowsCorrectBearerToken() throws Exception {
        var filter = new McpApiKeyAuthFilter(KEY);
        var req = new MockHttpServletRequest("POST", "/mcp");
        req.addHeader("Authorization", "Bearer " + KEY);
        assertThat(passedThrough(run(filter, req))).isTrue();
    }

    @Test
    void allowsCorrectApiKeyHeader() throws Exception {
        var filter = new McpApiKeyAuthFilter(KEY);
        var req = new MockHttpServletRequest("POST", "/mcp");
        req.addHeader("X-API-Key", KEY);
        assertThat(passedThrough(run(filter, req))).isTrue();
    }

    @Test
    void rejectsWrongKey() throws Exception {
        var filter = new McpApiKeyAuthFilter(KEY);
        var req = new MockHttpServletRequest("POST", "/mcp");
        req.addHeader("Authorization", "Bearer nope");
        var res = run(filter, req);
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getHeader("WWW-Authenticate")).contains("Bearer");
    }

    @Test
    void rejectsMissingKey() throws Exception {
        var filter = new McpApiKeyAuthFilter(KEY);
        var req = new MockHttpServletRequest("POST", "/mcp");
        assertThat(run(filter, req).getStatus()).isEqualTo(401);
    }

    @Test
    void alwaysAllowsHealthCheckEvenWhenEnabled() throws Exception {
        var filter = new McpApiKeyAuthFilter(KEY);
        var req = new MockHttpServletRequest("GET", "/actuator/health");
        assertThat(passedThrough(run(filter, req))).isTrue();
    }
}
