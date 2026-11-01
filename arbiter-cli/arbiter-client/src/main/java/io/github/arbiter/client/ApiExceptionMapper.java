package io.github.arbiter.client;

import io.github.arbiter.core.exception.AuthenticationException;
import io.github.arbiter.core.exception.AuthorizationException;
import io.github.arbiter.core.exception.ConflictException;
import io.github.arbiter.core.exception.DomainException;
import io.github.arbiter.core.exception.NetworkException;
import io.github.arbiter.core.exception.NotFoundException;
import io.github.arbiter.core.exception.RateLimitException;
import io.github.arbiter.core.exception.RemoteException;
import io.github.arbiter.core.exception.ServerException;

public final class ApiExceptionMapper {
    private ApiExceptionMapper() {
    }

    public static DomainException mapStatus(int status, String code, String message) {
        return switch (status) {
            case 401 -> new AuthenticationException(code, message);
            case 403 -> new AuthorizationException(code, message);
            case 404 -> new NotFoundException(code, message);
            case 409 -> new ConflictException(code, message);
            case 429 -> new RateLimitException(code, message);
            default -> {
                if (status >= 500 && status <= 599) {
                    yield new ServerException(code, message);
                }
                yield new RemoteException(code, message);
            }
        };
    }

    public static NetworkException network() {
        return new NetworkException();
    }
}
