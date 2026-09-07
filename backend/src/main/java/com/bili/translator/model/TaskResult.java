package com.bili.translator.model;

import java.util.List;

public class TaskResult {

    private String videoPath;
    private String srtPath;
    private String recognizedTextPath;
    private String burnedVideoPath;
    private List<SubtitleEntry> subtitles;
    private double duration;
<<<<<<< HEAD
=======
    private String completedSteps;
    private String audioPath;
>>>>>>> trae/solo-agent-DQFIa2

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

    public String getBurnedVideoPath() {
        return burnedVideoPath;
    }

    public void setBurnedVideoPath(String burnedVideoPath) {
        this.burnedVideoPath = burnedVideoPath;
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
<<<<<<< HEAD
=======

    public String getCompletedSteps() {
        return completedSteps;
    }

    public void setCompletedSteps(String completedSteps) {
        this.completedSteps = completedSteps;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public void setAudioPath(String audioPath) {
        this.audioPath = audioPath;
    }
>>>>>>> trae/solo-agent-DQFIa2
}
