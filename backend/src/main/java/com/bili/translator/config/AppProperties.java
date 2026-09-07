package com.bili.translator.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private static final Logger log = LoggerFactory.getLogger(AppProperties.class);

    private String dashscopeApiKey;
    private String openrouterApiKey;
    private String openrouterModel = "openai/gpt-oss-120b:free";
    private String tempDir = "./temp";
    private int maxVideoDuration = 3600;
    private int audioSegmentDuration = 300;
    private String asrModel = "paraformer-realtime-v2";
    private String translationModel = "qwen-plus";
    private String authUsername = "admin";
    private String authPassword;
    private String jwtSecret;
    private long jwtExpiration = 86400000;

    /**
     * 启动时兜底生成安全凭据,避免代码内置弱默认值:
     * - JWT 密钥未配置:生成随机密钥(重启后已签发 Token 失效,属预期行为)
     * - 登录密码未配置:生成随机密码并打印到日志,提示用户尽快配置固定密码
     */
    @PostConstruct
    void initSecurityDefaults() {
        SecureRandom random = new SecureRandom();

        if (jwtSecret == null || jwtSecret.isBlank()) {
            byte[] bytes = new byte[48];
            random.nextBytes(bytes);
            this.jwtSecret = Base64.getEncoder().encodeToString(bytes);
            log.warn("未配置 JWT_SECRET,已生成随机密钥(重启后已签发的 Token 将全部失效)");
        }

        if (authPassword == null || authPassword.isBlank()) {
            byte[] bytes = new byte[9];
            random.nextBytes(bytes);
            this.authPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            log.warn("未配置 AUTH_PASSWORD,本次启动使用随机密码: {} (请尽快在 .env 中配置固定密码)", authPassword);
        }
    }

    public String getDashscopeApiKey() {
        return dashscopeApiKey;
    }

    public void setDashscopeApiKey(String dashscopeApiKey) {
        this.dashscopeApiKey = dashscopeApiKey;
    }

    public String getOpenrouterApiKey() {
        return openrouterApiKey;
    }

    public void setOpenrouterApiKey(String openrouterApiKey) {
        this.openrouterApiKey = openrouterApiKey;
    }

    public String getOpenrouterModel() {
        return openrouterModel;
    }

    public void setOpenrouterModel(String openrouterModel) {
        this.openrouterModel = openrouterModel;
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public int getMaxVideoDuration() {
        return maxVideoDuration;
    }

    public void setMaxVideoDuration(int maxVideoDuration) {
        this.maxVideoDuration = maxVideoDuration;
    }

    public int getAudioSegmentDuration() {
        return audioSegmentDuration;
    }

    public void setAudioSegmentDuration(int audioSegmentDuration) {
        this.audioSegmentDuration = audioSegmentDuration;
    }

    public String getAsrModel() {
        return asrModel;
    }

    public void setAsrModel(String asrModel) {
        this.asrModel = asrModel;
    }

    public String getTranslationModel() {
        return translationModel;
    }

    public void setTranslationModel(String translationModel) {
        this.translationModel = translationModel;
    }

    public String getAuthUsername() {
        return authUsername;
    }

    public void setAuthUsername(String authUsername) {
        this.authUsername = authUsername;
    }

    public String getAuthPassword() {
        return authPassword;
    }

    public void setAuthPassword(String authPassword) {
        this.authPassword = authPassword;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtExpiration() {
        return jwtExpiration;
    }

    public void setJwtExpiration(long jwtExpiration) {
        this.jwtExpiration = jwtExpiration;
    }
}
