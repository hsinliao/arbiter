package io.github.arbiter.server.controller;

import io.github.arbiter.server.config.ServerConfig;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/** Serves the console entry points and the public UI bootstrap settings. */
@Controller
public final class ConsoleController {
    private final ServerConfig config;

    public ConsoleController(ServerConfig config) {
        this.config = config;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/home.html";
    }

    @GetMapping("/login")
    public String login() {
        return "redirect:/login.html";
    }

    @GetMapping("/system/users")
    public String users() {
        return "redirect:/system/users.html";
    }

    @GetMapping("/console/config")
    @ResponseBody
    public Map<String, String> consoleConfig() {
        return Map.of(
                "apiPrefix", config.apiPrefix(),
                "application", "arbiter-console");
    }
}
