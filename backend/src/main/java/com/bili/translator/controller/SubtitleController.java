package com.bili.translator.controller;

import com.bili.translator.model.SubtitleEntry;
import com.bili.translator.model.TaskResult;
import com.bili.translator.pipeline.PipelineProcessor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subtitle")
public class SubtitleController {

    private final PipelineProcessor processor;

    public SubtitleController(PipelineProcessor processor) {
        this.processor = processor;
    }

    @GetMapping("/{taskId}/srt")
    public ResponseEntity<Resource> downloadSrt(@PathVariable String taskId) {
        TaskResult result = processor.getTaskResult(taskId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        Path srtPath = Path.of(result.getSrtPath());
        if (!Files.exists(srtPath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(srtPath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + taskId + "_subtitles.srt\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }

    @GetMapping("/{taskId}/txt")
    public ResponseEntity<Resource> downloadRecognizedText(@PathVariable String taskId) {
        TaskResult result = processor.getTaskResult(taskId);
        if (result == null || result.getRecognizedTextPath() == null) {
            return ResponseEntity.notFound().build();
        }

        Path txtPath = Path.of(result.getRecognizedTextPath());
        if (!Files.exists(txtPath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(txtPath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + taskId + "_recognized.txt\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<Map<String, String>> saveSubtitle(@PathVariable String taskId, @RequestBody List<SubtitleEntry> subtitles) {
        boolean success = processor.updateSubtitles(taskId, subtitles);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "字幕已保存"));
    }
}
