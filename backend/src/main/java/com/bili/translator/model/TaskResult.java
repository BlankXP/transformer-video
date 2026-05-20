package com.bili.translator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TaskResult {
    @JsonProperty("video_path")
    private String videoPath;
    @JsonProperty("srt_path")
    private String srtPath;
    private List<SubtitleEntry> subtitles;
    private double duration;

    public String getVideoPath() { return videoPath; }
    public void setVideoPath(String videoPath) { this.videoPath = videoPath; }

    public String getSrtPath() { return srtPath; }
    public void setSrtPath(String srtPath) { this.srtPath = srtPath; }

    public List<SubtitleEntry> getSubtitles() { return subtitles; }
    public void setSubtitles(List<SubtitleEntry> subtitles) { this.subtitles = subtitles; }

    public double getDuration() { return duration; }
    public void setDuration(double duration) { this.duration = duration; }
}
