package com.bili.translator.controller;

import com.bili.translator.model.SubtitleEntry;
import com.bili.translator.model.TaskResult;
import com.bili.translator.service.PipelineProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class SubtitleController {

    private static final Logger log = LoggerFactory.getLogger(SubtitleController.class);

    private final PipelineProcessor pipelineProcessor;

    public SubtitleController(PipelineProcessor pipelineProcessor) {
        this.pipelineProcessor = pipelineProcessor;
    }

    @GetMapping("/{taskId}/subtitle")
    public ResponseEntity<Resource> downloadSrt(@PathVariable String taskId) {
        TaskResult result = pipelineProcessor.getTaskResult(taskId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        Path srtPath = Path.of(result.getSrtPath());
        if (!srtPath.toFile().exists()) {
            return ResponseEntity.notFound().build();
        }
        FileSystemResource resource = new FileSystemResource(srtPath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + taskId + "_subtitles.srt\"")
                .contentType(MediaType.parseMediaType("text/plain"))
                .body(resource);
    }

    @PutMapping("/{taskId}/subtitle")
    public ResponseEntity<Map<String, String>> saveSubtitle(@PathVariable String taskId,
                                                            @RequestBody Map<String, List<SubtitleEntry>> body) {
        List<SubtitleEntry> subtitles = body.get("subtitles");
        if (subtitles == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "subtitles field is required"));
        }
        boolean success = pipelineProcessor.updateSubtitles(taskId, subtitles);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "字幕已保存"));
    }
}
