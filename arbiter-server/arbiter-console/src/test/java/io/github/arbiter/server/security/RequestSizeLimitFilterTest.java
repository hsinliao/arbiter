package io.github.arbiter.server.security;

import io.github.arbiter.server.config.ServerConfig;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestSizeLimitFilterTest {
    private static final int LIMIT = 1024;

    @Test
    void rejectsBodiesAboveTheConfiguredLimit() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(config());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/auth/login");
        request.setContent(new byte[LIMIT + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                invoked.set(true));

        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8)
                .contains("PAYLOAD_TOO_LARGE"));
        assertFalse(invoked.get());
    }

    @Test
    void passesBodiesAtTheLimitAndCachesChunkedStyleBodies() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(config());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/auth/login") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        byte[][] observed = new byte[1][];

        filter.doFilter(request, response, (filteredRequest, ignoredResponse) -> {
            invoked.set(true);
            observed[0] = filteredRequest.getInputStream().readAllBytes();
        });

        assertEquals(200, response.getStatus());
        assertTrue(invoked.get());
        assertEquals("{\"ok\":true}",
                new String(observed[0], StandardCharsets.UTF_8));
    }

    private static ServerConfig config() {
        return new ServerConfig("127.0.0.1", 0, "/api/v1",
                Duration.ofHours(1), LIMIT, 5, Duration.ofMinutes(1));
    }
}
