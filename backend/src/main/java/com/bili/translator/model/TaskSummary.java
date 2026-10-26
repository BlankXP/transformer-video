package com.bili.translator.model;

/**
 * 任务摘要——列表接口的对外视图。
 *
 * <p>此前 `/tasks` 直接序列化整个 {@link TaskStatus}，会把
 * {@code result.video_path / srt_path / audio_path} 这些服务器绝对路径、
 * 以及全量字幕一起下发到浏览器：既泄露部署结构，又让 10 秒轮询的响应体膨胀到几十 MB。
 *
 * <p>这里只保留展示所需字段，路径一律折叠成 {@code has_*} 布尔。
 * 字幕内容仅在 {@link TaskDetail} 中按单个任务返回。
 */
public record TaskSummary(
        String taskId,
        String stage,
        double progress,
        String message,
        long createdAt,
        String url,
        String targetLanguage,
        boolean translateSubtitles,
        boolean hasVideo,
        boolean hasBurnedVideo,
        boolean hasSrt,
        boolean hasRecognizedText,
        double duration,
        int subtitleCount) {
}
