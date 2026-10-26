package com.bili.translator.controller;

import com.bili.translator.model.DownloadTicketRequest;
import com.bili.translator.model.TaskResult;
import com.bili.translator.pipeline.PipelineProcessor;
import com.bili.translator.service.AccessTicketService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 基于一次性票据的下载。
 *
 * <p>解决两个问题：浏览器直链下载带不了 Authorization 头；用 fetch + blob 又会把
 * 整个文件读进内存（烧录视频常有几百 MB）。票据方案让下载变成浏览器原生流式写盘。
 */
@RestController
@RequestMapping("/api")
public class DownloadController {

    private static final String OCTET_STREAM = MediaType.APPLICATION_OCTET_STREAM_VALUE;
    private static final String TEXT_UTF8 = "text/plain;charset=UTF-8";

    private final PipelineProcessor processor;
    private final AccessTicketService tickets;

    public DownloadController(PipelineProcessor processor, AccessTicketService tickets) {
        this.processor = processor;
        this.tickets = tickets;
    }

    /**
     * 用登录态换一张一次性票据。
     *
     * <p>返回的票据交给浏览器拼成 {@code /api/download/{ticket}} 直链，JWT 不进 URL。
     */
    @PostMapping("/download-ticket")
    public ResponseEntity<?> createTicket(@RequestBody DownloadTicketRequest request) {
        if (request.taskId() == null || request.taskId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "task_id 不能为空"));
        }

        TaskResult result = processor.getTaskResult(request.taskId());
        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        String kind = request.kind() == null ? "" : request.kind();
        String path;
        String filename;
        String contentType;

        switch (kind) {
            case "video" -> {
                path = result.getVideoPath();
                filename = request.taskId() + "_video.mp4";
                contentType = OCTET_STREAM;
            }
            case "burned" -> {
                path = result.getBurnedVideoPath();
                filename = request.taskId() + "_burned.mp4";
                contentType = OCTET_STREAM;
            }
            case "srt" -> {
                path = result.getSrtPath();
                filename = request.taskId() + "_subtitles.srt";
                contentType = TEXT_UTF8;
            }
            case "txt" -> {
                path = result.getRecognizedTextPath();
                filename = request.taskId() + "_recognized.txt";
                contentType = TEXT_UTF8;
            }
            default -> {
                return ResponseEntity.badRequest().body(Map.of("error", "不支持的下载类型：" + kind));
            }
        }

        if (path == null || path.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        String ticket = tickets.issue(path, filename, contentType);
        return ResponseEntity.ok(Map.of("ticket", ticket, "expires_in", tickets.ttlSeconds()));
    }

    /**
     * 凭票据下载，免登录——票据本身就是凭证，短时且一次性。
     *
     * <p>返回 {@link Resource} 由容器流式写出，不占应用内存。
     */
    @GetMapping("/download/{ticket}")
    public ResponseEntity<Resource> download(@PathVariable String ticket) {
        AccessTicketService.Ticket target = tickets.consume(ticket);
        if (target == null) {
            // 已使用或已过期，语义上都是「不再可用」
            return ResponseEntity.status(HttpStatus.GONE).build();
        }

        Path path = Path.of(target.path());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + target.filename() + "\"")
                .contentType(MediaType.parseMediaType(target.contentType()))
                .body(new FileSystemResource(path));
    }
}
