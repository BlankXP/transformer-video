package com.bili.translator.pipeline;

@FunctionalInterface
public interface ProgressCallback {
    void accept(String taskId, String stage, float progress, String message);
}
