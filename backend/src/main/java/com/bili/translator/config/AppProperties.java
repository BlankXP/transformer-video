package com.bili.translator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String dashscopeApiKey;
    private String openrouterApiKey;
    private String openrouterModel = "openai/gpt-oss-120b:free";
    private String tempDir = "./temp";
    private int maxVideoDuration = 3600;
    private int audioSegmentDuration = 300;
    private String asrModel = "paraformer-realtime-v2";
    /**
     * ASR 音频推送间隔（毫秒）。
     *
     * <p>每次推送 3200 字节 ≈ 0.1 秒音频，所以 100ms 恰好是 1:1 实时速率——
     * 60 分钟视频光识别就要 60 分钟，是整条流水线最大的瓶颈。
     * 20ms 约 5 倍速；设为 0 则尽快推完。推得过快可能触发服务端限流，
     * 若识别频繁失败可调回 50~100。
     */
    private int asrPushIntervalMs = 20;
    private String translationModel = "qwen-plus";
    private String authUsername = "admin";
    // 无默认值：必须由环境变量提供，缺失时启动失败
    private String authPassword;
    private String jwtSecret;
    private long jwtExpiration = 86400000;
    /** 任务（含文件）保留时长，超期由定时任务清理 */
    private int taskRetentionHours = 24;
    /**
     * 是否信任反向代理写入的 X-Real-IP。
     *
     * <p>默认 false：前端已合并进后端，应用可能直接对外暴露端口，此时
     * X-Forwarded-For / X-Real-IP 完全由客户端控制，不能当作限流身份；
     * 只有当前面确实有一层会覆盖写入该头的可信代理时才开启。
     */
    private boolean trustProxyHeaders = false;
    /**
     * 允许的跨域来源（逗号分隔）。
     *
     * <p>默认空：前端已合并进后端，页面与接口同源，浏览器不会发起跨域请求，
     * 也就不需要任何 CORS 头。只有前后端分离部署（设置了 VITE_API_BASE_URL）时才需要配置，
     * 例如 {@code https://app.example.com}。
     */
    private String allowedOrigins = "";

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

    public int getAsrPushIntervalMs() {
        return asrPushIntervalMs;
    }

    public void setAsrPushIntervalMs(int asrPushIntervalMs) {
        this.asrPushIntervalMs = asrPushIntervalMs;
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

    public int getTaskRetentionHours() {
        return taskRetentionHours;
    }

    public void setTaskRetentionHours(int taskRetentionHours) {
        this.taskRetentionHours = taskRetentionHours;
    }

    public boolean isTrustProxyHeaders() {
        return trustProxyHeaders;
    }

    public void setTrustProxyHeaders(boolean trustProxyHeaders) {
        this.trustProxyHeaders = trustProxyHeaders;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
