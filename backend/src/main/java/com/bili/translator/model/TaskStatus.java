package com.bili.translator.model;

public class TaskStatus {

    private String taskId;
    private String stage;
    private double progress = 0.0;
    private String message = "";
    private TaskResult result;
<<<<<<< HEAD
=======
    private ProcessRequest request;
    private long createdAt;
>>>>>>> trae/solo-agent-DQFIa2

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public TaskResult getResult() {
        return result;
    }

    public void setResult(TaskResult result) {
        this.result = result;
    }
<<<<<<< HEAD
=======

    public ProcessRequest getRequest() {
        return request;
    }

    public void setRequest(ProcessRequest request) {
        this.request = request;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
>>>>>>> trae/solo-agent-DQFIa2
}
