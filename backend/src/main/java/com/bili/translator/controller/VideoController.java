package com.bili.translator.controller;

import com.bili.translator.config.AppProperties;
import com.bili.translator.model.ProcessRequest;
import com.bili.translator.model.TaskResult;
import com.bili.translator.model.TaskStatus;
import com.bili.translator.pipeline.PipelineProcessor;
import com.bili.translator.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/video")
public class VideoController {

    private final PipelineProcessor processor;
    private final AppProperties appProperties;

    public VideoController(PipelineProcessor processor, AppProperties appProperties) {
        this.processor = processor;
        this.appProperties = appProperties;
    }

    @PostMapping("/process")
    public ResponseEntity<?> processVideo(@RequestBody ProcessRequest request, HttpServletRequest httpRequest) {
        if (request.isTranslateSubtitles()) {
            String token = extractToken(httpRequest);
            if (token == null || !JwtUtil.validateToken(token, appProperties.getJwtSecret())) {
                return ResponseEntity.status(401).body(Map.of("error", "翻译字幕需要登录，请先登录"));
            }
        }

        String taskId = processor.createTask(request);
        processor.process(taskId, request);
        return ResponseEntity.ok(Map.of("task_id", taskId));
    }

    @GetMapping("/{taskId}/status")
    public ResponseEntity<TaskStatus> getTaskStatus(@PathVariable String taskId) {
        TaskStatus status = processor.getTaskStatus(taskId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    @GetMapping("/{taskId}/burned")
    public ResponseEntity<Resource> downloadBurnedVideo(@PathVariable String taskId) {
        TaskResult result = processor.getTaskResult(taskId);
        if (result == null || result.getBurnedVideoPath() == null) {
            return ResponseEntity.notFound().build();
        }

        Path videoPath = Path.of(result.getBurnedVideoPath());
        if (!Files.exists(videoPath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(videoPath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + taskId + "_burned.mp4\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        String queryToken = request.getParameter("token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken;
        }

        return null;
    }
}
