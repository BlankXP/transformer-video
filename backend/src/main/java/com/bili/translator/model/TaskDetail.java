package com.bili.translator.model;

import java.util.List;

/**
 * 任务详情——单任务查询接口的对外视图。
 *
 * <p>与 {@link TaskSummary} 的差别只有两点：多带一份字幕内容（前端编辑器需要），
 * 但仍然不下发任何服务器路径。前端要判断"能不能播放/下载"看 {@code has_*} 即可。
 */
public record TaskDetail(
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
        List<SubtitleEntry> subtitles) {
}
