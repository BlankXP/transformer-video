package com.bili.translator.controller;

import com.bili.translator.model.ProcessRequest;
import com.bili.translator.model.TaskStatus;
import com.bili.translator.pipeline.PipelineProcessor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/video")
public class VideoController {

    private final PipelineProcessor processor;

    public VideoController(PipelineProcessor processor) {
        this.processor = processor;
    }

    @PostMapping("/process")
    public Map<String, String> processVideo(@RequestBody ProcessRequest request) {
        String taskId = processor.createTask(request);
        processor.process(taskId, request);
        return Map.of("task_id", taskId);
    }

    @GetMapping("/{taskId}/status")
    public ResponseEntity<TaskStatus> getTaskStatus(@PathVariable String taskId) {
        TaskStatus status = processor.getTaskStatus(taskId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}
