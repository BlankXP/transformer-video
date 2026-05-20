package com.bili.translator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String dashscopeApiKey = "";
    private String tempDir = "./temp";
    private int maxVideoDuration = 3600;
    private int audioSegmentDuration = 300;
    private String asrModel = "paraformer-v2";
    private String translationModel = "qwen-plus";

    public String getDashscopeApiKey() { return dashscopeApiKey; }
    public void setDashscopeApiKey(String dashscopeApiKey) { this.dashscopeApiKey = dashscopeApiKey; }

    public String getTempDir() { return tempDir; }
    public void setTempDir(String tempDir) { this.tempDir = tempDir; }

    public int getMaxVideoDuration() { return maxVideoDuration; }
    public void setMaxVideoDuration(int maxVideoDuration) { this.maxVideoDuration = maxVideoDuration; }

    public int getAudioSegmentDuration() { return audioSegmentDuration; }
    public void setAudioSegmentDuration(int audioSegmentDuration) { this.audioSegmentDuration = audioSegmentDuration; }

    public String getAsrModel() { return asrModel; }
    public void setAsrModel(String asrModel) { this.asrModel = asrModel; }

    public String getTranslationModel() { return translationModel; }
    public void setTranslationModel(String translationModel) { this.translationModel = translationModel; }
}
