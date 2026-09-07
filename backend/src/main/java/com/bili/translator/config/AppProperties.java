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
    private String translationModel = "qwen-plus";
<<<<<<< HEAD
=======
    private String authUsername = "admin";
    private String authPassword = "admin123";
    private String jwtSecret = "bili-translator-default-jwt-secret-key-2024";
    private long jwtExpiration = 86400000;
>>>>>>> trae/solo-agent-DQFIa2

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
<<<<<<< HEAD
=======

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
>>>>>>> trae/solo-agent-DQFIa2
}
