package io.github.arbiter.server.error;

import io.github.arbiter.api.ApiErrorCodes;
import io.github.arbiter.api.dto.ApiErrorResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public final class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(ApiException error) {
        return response(error.status(), error.code(), error.safeMessage(),
                error.headers());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableBody() {
        return response(400, ApiErrorCodes.INVALID_REQUEST,
                "Invalid request body.", Map.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException error) {
        return response(400, ApiErrorCodes.INVALID_ARGUMENT,
                "Missing required parameter: " + error.getParameterName(), Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException error) {
        return response(400, ApiErrorCodes.INVALID_ARGUMENT,
                "Invalid value for parameter: " + error.getName(), Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException error) {
        String allowed = error.getSupportedHttpMethods() == null ? null
                : error.getSupportedHttpMethods().stream()
                .map(Object::toString).reduce((a, b) -> a + ", " + b).orElse(null);
        return response(405, ApiErrorCodes.METHOD_NOT_ALLOWED,
                "HTTP method is not allowed for this endpoint.",
                allowed == null ? Map.of() : Map.of(HttpHeaders.ALLOW, allowed));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiErrorResponse> handleNotFound() {
        return response(404, ApiErrorCodes.NOT_FOUND,
                "API endpoint was not found.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception error) {
        LOG.error("Unhandled request failure", error);
        return response(500, ApiErrorCodes.INTERNAL_ERROR,
                "The server could not complete the request.", Map.of());
    }

    private static ResponseEntity<ApiErrorResponse> response(
            int status, String code, String message, Map<String, String> extraHeaders) {
        Map<String, String> headers = new LinkedHashMap<>(extraHeaders);
        headers.put("Cache-Control", "no-store");
        headers.put("X-Content-Type-Options", "nosniff");
        return ResponseEntity.status(status)
                .headers(toHttpHeaders(headers))
                .contentType(MediaType.parseMediaType(
                        "application/json; charset=UTF-8"))
                .body(new ApiErrorResponse(code, message));
    }

    private static HttpHeaders toHttpHeaders(Map<String, String> values) {
        HttpHeaders headers = new HttpHeaders();
        values.forEach(headers::set);
        return headers;
    }
}
