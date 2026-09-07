package com.bili.translator.controller;

import com.bili.translator.config.AppProperties;
import com.bili.translator.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppProperties appProperties;

    public AuthController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return ResponseEntity.status(400).body(Map.of("error", "用户名和密码不能为空"));
        }

        if (!username.equals(appProperties.getAuthUsername()) || !password.equals(appProperties.getAuthPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }

        String token = JwtUtil.generateToken(username, appProperties.getJwtSecret(), appProperties.getJwtExpiration());
        return ResponseEntity.ok(Map.of("token", token, "username", username));
    }
}
