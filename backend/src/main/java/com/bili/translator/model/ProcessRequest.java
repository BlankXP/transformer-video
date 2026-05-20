package com.bili.translator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProcessRequest {
    private String url;
    @JsonProperty("source_language")
    private String sourceLanguage;
    @JsonProperty("target_language")
    private String targetLanguage;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getSourceLanguage() { return sourceLanguage; }
    public void setSourceLanguage(String sourceLanguage) { this.sourceLanguage = sourceLanguage; }

    public String getTargetLanguage() { return targetLanguage; }
    public void setTargetLanguage(String targetLanguage) { this.targetLanguage = targetLanguage; }
}
