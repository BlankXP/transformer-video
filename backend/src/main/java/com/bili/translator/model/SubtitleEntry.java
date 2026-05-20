package com.bili.translator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SubtitleEntry {
    private int index;
    @JsonProperty("start_time")
    private String startTime;
    @JsonProperty("end_time")
    private String endTime;
    @JsonProperty("source_text")
    private String sourceText;
    @JsonProperty("translated_text")
    private String translatedText;

    public SubtitleEntry() {}

    public SubtitleEntry(int index, String startTime, String endTime, String sourceText, String translatedText) {
        this.index = index;
        this.startTime = startTime;
        this.endTime = endTime;
        this.sourceText = sourceText;
        this.translatedText = translatedText;
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getSourceText() { return sourceText; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }

    public String getTranslatedText() { return translatedText; }
    public void setTranslatedText(String translatedText) { this.translatedText = translatedText; }
}
