package io.github.arbiter.server.uds;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.github.arbiter.api.ApiErrorCodes;
import io.github.arbiter.api.ApiHeaders;
import io.github.arbiter.api.ApiRoutes;
import io.github.arbiter.api.dto.ApiErrorResponse;
import io.github.arbiter.api.dto.LoginRequest;
import io.github.arbiter.api.dto.UserCreateRequest;
import io.github.arbiter.api.dto.UserUpdateRequest;
import io.github.arbiter.api.uds.UdsRequest;
import io.github.arbiter.api.uds.UdsResponse;
import io.github.arbiter.server.auth.TokenService.TokenClaims;
import io.github.arbiter.server.config.ServerConfig;
import io.github.arbiter.server.error.ApiException;
import io.github.arbiter.server.service.AuthService;
import io.github.arbiter.server.service.QueryService;
import io.github.arbiter.server.service.StatusService;
import io.github.arbiter.server.service.UserService;

import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unix-domain socket transport adapter. It maps the framed UDS request to the
 * same services used by the Spring MVC controllers.
 */
@Component
public final class UdsRequestDispatcher {
    private final ServerConfig config;
    private final int maxRequestBytes;
    private final AuthService authService;
    private final UserService userService;
    private final StatusService statusService;
    private final QueryService queryService;
    private final ObjectMapper mapper = new ObjectMapper();

    public UdsRequestDispatcher(ServerConfig config, AuthService authService,
                                UserService userService, StatusService statusService,
                                QueryService queryService) {
        this.config = config;
        this.maxRequestBytes = config.maxRequestBytes();
        this.authService = authService;
        this.userService = userService;
        this.statusService = statusService;
        this.queryService = queryService;
    }

    public UdsResponse handle(UdsRequest request) {
        try {
            enforceBodyLimit(request);
            String route = route(request.path());
            String method = request.method();
            if (ApiRoutes.LOGIN.equals(route)) {
                requireMethod(method, "POST");
                return json(200, authService.login(
                        body(request, LoginRequest.class), "unix"));
            }
            if (ApiRoutes.WHO_AM_I.equals(route)) {
                requireMethod(method, "GET");
                return json(200, authService.whoAmI(authenticate(request)));
            }
            if (ApiRoutes.STATUS.equals(route)) {
                requireMethod(method, "GET");
                authenticate(request, "reader", "admin");
                return json(200, statusService.status());
            }
            if (ApiRoutes.QUERY.equals(route)) {
                requireMethod(method, "GET");
                authenticate(request, "reader", "admin");
                return json(200, queryService.query(queryName(request.path())));
            }
            if (ApiRoutes.USERS.equals(route)) {
                authenticate(request, "admin");
                if ("GET".equalsIgnoreCase(method)) {
                    return json(200, userService.list());
                }
                requireMethod(method, "POST");
                return json(201, userService.create(body(request, UserCreateRequest.class)));
            }
            if (route.startsWith(ApiRoutes.USERS + "/")) {
                TokenClaims admin = authenticate(request, "admin");
                String username = URLDecoder.decode(
                        route.substring(ApiRoutes.USERS.length() + 1),
                        StandardCharsets.UTF_8);
                if ("GET".equalsIgnoreCase(method)) {
                    return json(200, userService.get(username));
                }
                if ("DELETE".equalsIgnoreCase(method)) {
                    return json(200, userService.delete(username, admin.subject()));
                }
                requireMethod(method, "PUT");
                return json(200, userService.update(username,
                        body(request, UserUpdateRequest.class), admin.subject()));
            }
            throw new ApiException(404, ApiErrorCodes.NOT_FOUND,
                    "API endpoint was not found.");
        } catch (ApiException error) {
            return error(error.status(), error.code(), error.safeMessage(),
                    error.headers());
        } catch (RuntimeException unexpected) {
            return error(500, ApiErrorCodes.INTERNAL_ERROR,
                    "The server could not complete the request.", Map.of());
        }
    }

    private void enforceBodyLimit(UdsRequest request) {
        if (request.body() != null && request.body()
                .getBytes(StandardCharsets.UTF_8).length > maxRequestBytes) {
            throw new ApiException(413, ApiErrorCodes.PAYLOAD_TOO_LARGE,
                    "Request body is too large.");
        }
    }

    private TokenClaims authenticate(UdsRequest request) {
        return authService.requireToken(request.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(ApiHeaders.ARBITER_TOKEN))
                .map(Map.Entry::getValue).findFirst().orElse(null));
    }

    private TokenClaims authenticate(UdsRequest request, String... scopes) {
        return authService.requireScopes(request.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(ApiHeaders.ARBITER_TOKEN))
                .map(Map.Entry::getValue).findFirst().orElse(null), scopes);
    }

    private <T> T body(UdsRequest request, Class<T> type) {
        try {
            JsonNode body = parseBody(request.body());
            return mapper.treeToValue(body, type);
        } catch (JacksonException invalid) {
            throw new ApiException(400, ApiErrorCodes.INVALID_REQUEST,
                    "Invalid request body.");
        }
    }

    private JsonNode parseBody(String body) {
        if (body == null || body.isBlank()) {
            throw new ApiException(400, ApiErrorCodes.INVALID_REQUEST,
                    "Request body must be a JSON object.");
        }
        try {
            JsonNode node = mapper.readTree(body);
            if (node == null || !node.isObject()) {
                throw new ApiException(400, ApiErrorCodes.INVALID_REQUEST,
                        "Request body must be a JSON object.");
            }
            return node;
        } catch (JacksonException invalid) {
            throw new ApiException(400, ApiErrorCodes.INVALID_REQUEST,
                    "Invalid request body.");
        }
    }

    private String queryName(String requestPath) {
        Map<String, List<String>> parameters;
        try {
            parameters = queryParameters(requestPath);
        } catch (IllegalArgumentException invalidEncoding) {
            throw new ApiException(400, ApiErrorCodes.INVALID_REQUEST,
                    "Invalid query string.");
        }
        return parameters.getOrDefault("name", List.of("")).stream()
                .findFirst().orElse("");
    }

    private UdsResponse json(int status, Object body) {
        try {
            return new UdsResponse(status, headers(), mapper.writeValueAsString(body));
        } catch (JacksonException failure) {
            return error(500, ApiErrorCodes.INTERNAL_ERROR,
                    "The server could not complete the request.", Map.of());
        }
    }

    private UdsResponse error(int status, String code, String message,
                              Map<String, String> extraHeaders) {
        try {
            return new UdsResponse(status, headers(extraHeaders),
                    mapper.writeValueAsString(new ApiErrorResponse(code, message)));
        } catch (JacksonException failure) {
            return new UdsResponse(status, headers(extraHeaders),
                    "{\"code\":\"INTERNAL_ERROR\","
                            + "\"message\":\"The server could not complete the request.\"}");
        }
    }

    private static Map<String, String> headers() {
        return headers(Map.of());
    }

    private static Map<String, String> headers(Map<String, String> extra) {
        Map<String, String> headers = new LinkedHashMap<>(extra);
        headers.put("Content-Type", "application/json; charset=UTF-8");
        headers.put("Cache-Control", "no-store");
        headers.put("X-Content-Type-Options", "nosniff");
        return headers;
    }

    private String route(String requestPath) {
        int query = requestPath.indexOf('?');
        String path = query < 0 ? requestPath : requestPath.substring(0, query);
        String prefix = config.apiPrefix();
        if ("/".equals(prefix)) {
            return path;
        }
        if (!path.equals(prefix) && !path.startsWith(prefix + "/")) {
            return "";
        }
        String route = path.substring(prefix.length());
        return route.isEmpty() ? "/" : route;
    }

    private static void requireMethod(String actual, String expected) {
        if (!expected.equalsIgnoreCase(actual)) {
            throw new ApiException(405, ApiErrorCodes.METHOD_NOT_ALLOWED,
                    "HTTP method is not allowed for this endpoint.",
                    Map.of("Allow", expected));
        }
    }

    private static Map<String, List<String>> queryParameters(String requestPath) {
        int query = requestPath.indexOf('?');
        if (query < 0 || query == requestPath.length() - 1) {
            return Map.of();
        }
        String raw = requestPath.substring(query + 1);
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2
                    ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return values;
    }
}
