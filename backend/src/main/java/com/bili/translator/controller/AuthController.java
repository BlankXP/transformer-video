package com.bili.translator.controller;

import com.bili.translator.config.AppProperties;
import com.bili.translator.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** 连续失败多少次后锁定 */
    private static final int MAX_FAILURES = 5;
    /** 锁定时长 */
    private static final long LOCK_MILLIS = 5 * 60 * 1000L;

    private final AppProperties appProperties;

    /** ip -> [连续失败次数, 锁定截止时间] */
    private final Map<String, long[]> attempts = new ConcurrentHashMap<>();

    public AuthController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
        long[] record = attempts.computeIfAbsent(ip, k -> new long[2]);
        long now = System.currentTimeMillis();

        synchronized (record) {
            if (record[1] > now) {
                long remainSeconds = (record[1] - now) / 1000 + 1;
                return ResponseEntity.status(429)
                        .body(Map.of("error", "尝试过于频繁，请 " + remainSeconds + " 秒后再试"));
            }
        }

        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return ResponseEntity.status(400).body(Map.of("error", "用户名和密码不能为空"));
        }

        // 两个条件都要计算，避免短路比较泄露「用户名是否正确」
        boolean usernameMatches = safeEquals(username, appProperties.getAuthUsername());
        boolean passwordMatches = safeEquals(password, appProperties.getAuthPassword());

        if (!usernameMatches || !passwordMatches) {
            synchronized (record) {
                record[0]++;
                if (record[0] >= MAX_FAILURES) {
                    record[1] = now + LOCK_MILLIS;
                    record[0] = 0;
                    log.warn("登录失败次数超限，锁定 {} 分钟: ip={}", LOCK_MILLIS / 60000, ip);
                }
            }
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }

        attempts.remove(ip);
        String token = JwtUtil.generateToken(username, appProperties.getJwtSecret(), appProperties.getJwtExpiration());
        return ResponseEntity.ok(Map.of("token", token, "username", username));
    }

    /**
     * 常量时间比较，避免通过响应耗时逐字符探测口令。
     */
    private static boolean safeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
