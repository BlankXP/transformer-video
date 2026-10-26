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
    /** 失败计数多久没有新失败就丢弃：既让 Map 有界，也让「连续失败」有合理的统计窗口 */
    private static final long FAILURE_TTL_MILLIS = 60 * 60 * 1000L;
    /** 超过这个规模才触发惰性清理，避免每次登录都扫一遍 */
    private static final int PURGE_THRESHOLD = 256;

    private final AppProperties appProperties;

    /** ip -> [连续失败次数, 锁定截止时间, 最后失败时间]（包级可见供测试构造数据） */
    final Map<String, long[]> attempts = new ConcurrentHashMap<>();

    public AuthController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
        long now = System.currentTimeMillis();
        purgeExpired(now);

        long[] record = attempts.computeIfAbsent(ip, k -> new long[]{0, 0, now});

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
                record[2] = now;
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

    /**
     * 客户端 IP，仅用于登录失败限流。
     *
     * <p>前端合并进后端后不再有 nginx 覆盖 {@code X-Forwarded-For}，该头完全由客户端提供，
     * 每个请求换一个值就能绕开「5 次锁定」，因此默认只认 TCP 层的 remoteAddr；
     * 只有当前面确实有一层会覆盖写入 {@code X-Real-IP} 的可信代理时才通过配置开启。
     */
    private String clientIp(HttpServletRequest request) {
        if (appProperties.isTrustProxyHeaders()) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 惰性清理失败记录。
     *
     * <p>没有这一步时，每个曾经失败过一次的 IP 都会永久留在 Map 里；公网暴露下
     * 扫描器会带来大量不同来源 IP，内存只增不减（每条记录约百字节，百万级即上百 MB）。
     *
     * <p>只清「未在锁定中、且超过 TTL 没有新失败」的条目：锁定中的记录必须留到锁定期结束。
     *
     * <p>包级可见：单测直接注入时间，不依赖系统时钟。
     */
    void purgeExpired(long now) {
        if (attempts.size() < PURGE_THRESHOLD) {
            return;
        }
        attempts.entrySet().removeIf(entry -> {
            long[] record = entry.getValue();
            synchronized (record) {
                return record[1] <= now && now - record[2] > FAILURE_TTL_MILLIS;
            }
        });
    }
}
