package com.bili.translator.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 启动期安全配置校验。
 *
 * <p>application.yml 中的 auth-password / jwt-secret 已移除默认值，这里负责在容器启动阶段
 * 拒绝弱配置，避免出现「空密码可登录」或「使用示例密钥签发的令牌全网通用」这类事故。
 */
@Component
public class StartupConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigValidator.class);

    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MIN_SECRET_BYTES = 32;
    private static final long MAX_EXPIRATION_MS = 7L * 24 * 3600 * 1000;

    /** 历史遗留的示例密钥，出现即视为未配置 */
    private static final List<String> FORBIDDEN_SECRET_MARKERS = List.of(
            "default", "bili-translator", "change-me", "changeme", "secret-key");

    private final AppProperties appProperties;

    public StartupConfigValidator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void validate() {
        String password = appProperties.getAuthPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "AUTH_PASSWORD 未配置，拒绝启动。请设置长度不少于 " + MIN_PASSWORD_LENGTH + " 位的登录口令");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "AUTH_PASSWORD 长度不足 " + MIN_PASSWORD_LENGTH + " 位，拒绝启动");
        }
        // 项目历史上的默认口令（8 位）与常见弱口令都不足 12 位，长度校验已覆盖；
        // 这里不再硬编码具体口令，避免把历史凭据重新写回源码


        String secret = appProperties.getJwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET 未配置，拒绝启动。HS256 至少需要 " + MIN_SECRET_BYTES + " 字节密钥");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 不足 " + MIN_SECRET_BYTES + " 字节（HS256 最低要求），拒绝启动");
        }
        String lower = secret.toLowerCase();
        for (String marker : FORBIDDEN_SECRET_MARKERS) {
            if (lower.contains(marker)) {
                throw new IllegalStateException("JWT_SECRET 疑似示例值（包含 \"" + marker + "\"），拒绝启动");
            }
        }

        long expiration = appProperties.getJwtExpiration();
        if (expiration <= 0 || expiration > MAX_EXPIRATION_MS) {
            throw new IllegalStateException("JWT_EXPIRATION 应在 1 毫秒到 7 天之间，当前为 " + expiration);
        }

        if ("admin".equals(appProperties.getAuthUsername())) {
            log.warn("登录用户名仍为默认值 admin，建议通过 AUTH_USERNAME 修改");
        }
        if (appProperties.getDashscopeApiKey() == null || appProperties.getDashscopeApiKey().isBlank()) {
            log.warn("DASHSCOPE_API_KEY 未配置，语音识别与翻译功能将不可用");
        }
        log.info("安全配置校验通过");
    }
}
