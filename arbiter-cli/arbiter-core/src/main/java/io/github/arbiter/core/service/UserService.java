package io.github.arbiter.core.service;

import java.util.List;

public interface UserService {
    List<UserInfo> list();

    UserInfo show(String username);

    UserInfo create(String username, String password, String scope, boolean enabled);

    UserInfo update(String username, String password, String scope, Boolean enabled);

    void delete(String username);
}
