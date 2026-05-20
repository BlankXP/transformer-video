package com.bili.translator.controller;

import com.bili.translator.model.ProcessRequest;
import com.bili.translator.model.TaskStatus;
import com.bili.translator.service.PipelineProcessor;
import com.bili.translator.websocket.ProgressWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final PipelineProcessor pipelineProcessor;
    private final ProgressWebSocketHandler webSocketHandler;

    public TaskController(PipelineProcessor pipelineProcessor, ProgressWebSocketHandler webSocketHandler) {
        this.pipelineProcessor = pipelineProcessor;
        this.webSocketHandler = webSocketHandler;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> processVideo(@RequestBody ProcessRequest request) {
        String taskId = pipelineProcessor.createTask(request);
        pipelineProcessor.registerProgressCallback(taskId, (tid, stage, progress, message) -> {
            webSocketHandler.sendProgress(tid, stage, progress, message);
        });
        new Thread(() -> pipelineProcessor.process(taskId, request)).start();
        return ResponseEntity.ok(Map.of("task_id", taskId));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskStatus> getTaskStatus(@PathVariable String taskId) {
        TaskStatus status = pipelineProcessor.getTaskStatus(taskId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}
