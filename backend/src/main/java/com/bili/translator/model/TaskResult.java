package com.bili.translator.model;

import java.util.List;

public class TaskResult {

    private String videoPath;
    private String srtPath;
    private String recognizedTextPath;
    private List<SubtitleEntry> subtitles;
    private double duration;

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public String getSrtPath() {
        return srtPath;
    }

    public void setSrtPath(String srtPath) {
        this.srtPath = srtPath;
    }

    public String getRecognizedTextPath() {
        return recognizedTextPath;
    }

    public void setRecognizedTextPath(String recognizedTextPath) {
        this.recognizedTextPath = recognizedTextPath;
    }

    public List<SubtitleEntry> getSubtitles() {
        return subtitles;
    }

    public void setSubtitles(List<SubtitleEntry> subtitles) {
        this.subtitles = subtitles;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }
}
