package io.github.arbiter.server.security;

import io.github.arbiter.api.ApiErrorCodes;
import io.github.arbiter.server.config.ServerConfig;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Rejects API bodies above {@code arbiter.server.max.request.bytes}. The
 * Content-Length fast path avoids reading oversized requests; chunked bodies
 * are read once and replayed to the controller.
 */
@Component
public final class RequestSizeLimitFilter extends OncePerRequestFilter {
    private static final byte[] TOO_LARGE = ("{\"code\":\""
            + ApiErrorCodes.PAYLOAD_TOO_LARGE + "\",\"message\":"
            + "\"Request body is too large.\"}").getBytes(StandardCharsets.UTF_8);

    private final int maxBytes;

    public RequestSizeLimitFilter(ServerConfig config) {
        this.maxBytes = config.maxRequestBytes();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maxBytes) {
            tooLarge(response);
            return;
        }
        if (declaredLength >= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            tooLarge(response);
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private static void tooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getOutputStream().write(TOO_LARGE);
        response.flushBuffer();
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) {
                    return input.read(bytes, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Async reads are not supported");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
