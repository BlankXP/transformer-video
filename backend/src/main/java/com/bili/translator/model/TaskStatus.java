package com.bili.translator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskStatus {
    @JsonProperty("task_id")
    private String taskId;
    private String stage;
    private double progress;
    private String message;
    private TaskResult result;

    public TaskStatus() {}

    public TaskStatus(String taskId, String stage, double progress, String message) {
        this.taskId = taskId;
        this.stage = stage;
        this.progress = progress;
        this.message = message;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public double getProgress() { return progress; }
    public void setProgress(double progress) { this.progress = progress; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public TaskResult getResult() { return result; }
    public void setResult(TaskResult result) { this.result = result; }
}
