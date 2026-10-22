package com.bili.translator.controller;

import com.bili.translator.model.ProcessRequest;
import com.bili.translator.model.TaskResult;
import com.bili.translator.model.TaskStatus;
import com.bili.translator.pipeline.PipelineProcessor;
import com.bili.translator.util.VideoUrlValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/video")
public class VideoController {

    private static final Logger log = LoggerFactory.getLogger(VideoController.class);

    private final PipelineProcessor processor;

    public VideoController(PipelineProcessor processor) {
        this.processor = processor;
    }

    @PostMapping("/process")
    public ResponseEntity<?> processVideo(@RequestBody ProcessRequest request) {
        String normalizedUrl;
        try {
            normalizedUrl = VideoUrlValidator.validateAndNormalize(request.getUrl());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        request.setUrl(normalizedUrl);

        PipelineProcessor.CreateResult created = processor.createOrGetTask(request);
        if (created.alreadyExists()) {
            return ResponseEntity.ok(Map.of("task_id", created.taskId(), "already_exists", true));
        }

        processor.process(created.taskId(), request);
        return ResponseEntity.ok(Map.of("task_id", created.taskId()));
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<TaskStatus>> listTasks() {
        return ResponseEntity.ok(processor.getAllTasks());
    }

    @GetMapping("/find")
    public ResponseEntity<?> findByUrl(@RequestParam String url) {
        TaskStatus status = processor.findByUrl(url);
        if (status == null) {
            return ResponseEntity.ok(Map.of("exists", false));
        }
        // 只回必要字段，避免把输入 URL、服务器路径、字幕内容一并吐出
        return ResponseEntity.ok(Map.of(
                "exists", true,
                "task_id", status.getTaskId(),
                "stage", status.getStage()));
    }

    @PostMapping("/{taskId}/retry")
    public ResponseEntity<?> retryTask(@PathVariable String taskId) {
        TaskStatus status = processor.getTaskStatus(taskId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        if (!"failed".equals(status.getStage())) {
            return ResponseEntity.status(400).body(Map.of("error", "只能重试失败的任务"));
        }

        ProcessRequest request = status.getRequest();

        status.setStage("downloading");
        status.setProgress(0.0);
        status.setMessage("正在重试任务...");
        processor.persistTask(taskId);

        if (request != null) {
            processor.process(taskId, request);
        } else {
            return ResponseEntity.status(400).body(Map.of("error", "任务请求参数缺失，无法重试"));
        }

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

    @DeleteMapping("/{taskId}")
    public ResponseEntity<?> deleteTask(@PathVariable String taskId) {
        boolean deleted;
        try {
            deleted = processor.deleteTask(taskId);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "任务已删除"));
    }

    @GetMapping("/{taskId}/stream")
    public ResponseEntity<StreamingResponseBody> streamVideo(@PathVariable String taskId, HttpServletRequest request) {
        TaskResult result = processor.getTaskResult(taskId);
        if (result == null || result.getVideoPath() == null) {
            return ResponseEntity.notFound().build();
        }

        Path videoPath = Path.of(result.getVideoPath());
        if (!Files.exists(videoPath)) {
            return ResponseEntity.notFound().build();
        }

        long fileSize;
        try {
            fileSize = Files.size(videoPath);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }

        String rangeHeader = request.getHeader("Range");

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            try {
                String byteRange = rangeHeader.substring(6);
                String[] parts = byteRange.split("-");
                long start = Long.parseLong(parts[0].trim());
                long end = parts.length > 1 && !parts[1].isBlank()
                        ? Long.parseLong(parts[1].trim()) : fileSize - 1;

                if (start >= fileSize || end >= fileSize || start > end) {
                    return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                            .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                            .build();
                }

                long contentLength = end - start + 1;

                StreamingResponseBody body = outputStream -> {
                    try (RandomAccessFile raf = new RandomAccessFile(videoPath.toFile(), "r")) {
                        raf.seek(start);
                        byte[] buffer = new byte[8192];
                        long remaining = contentLength;
                        while (remaining > 0) {
                            int toRead = (int) Math.min(buffer.length, remaining);
                            int read = raf.read(buffer, 0, toRead);
                            if (read == -1) break;
                            outputStream.write(buffer, 0, read);
                            remaining -= read;
                        }
                        outputStream.flush();
                    } catch (IOException e) {
                        log.debug("视频流写入中断 (客户端断开): {}", e.getMessage());
                    }
                };

                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                        .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .body(body);
            } catch (NumberFormatException e) {
                log.warn("无效的 Range 头: {}", rangeHeader);
            }
        }

        StreamingResponseBody body = outputStream -> {
            try (RandomAccessFile raf = new RandomAccessFile(videoPath.toFile(), "r")) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = raf.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
            } catch (IOException e) {
                log.debug("视频流写入中断 (客户端断开): {}", e.getMessage());
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(body);
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
}
