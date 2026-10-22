package com.bili.translator.service;

import com.bili.translator.config.AppProperties;
import com.bili.translator.util.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class VideoDownloader {

    private static final Logger log = LoggerFactory.getLogger(VideoDownloader.class);

    private static final Pattern PERCENT = Pattern.compile("(\\d+\\.?\\d*)%");

    private final AppProperties appProperties;

    public VideoDownloader(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public Path download(String url, Path outputDir, BiConsumer<Float, String> progressCallback) throws Exception {
        if (url.startsWith("BV") || url.startsWith("bv")) {
            url = "https://www.bilibili.com/video/" + url;
        }

        enforceMaxDuration(url);

        String outputPath = outputDir.resolve("video.%(ext)s").toString();

        ProcessRunner.ProcessResult result = ProcessRunner.run(
            List.of("yt-dlp",
                "-o", outputPath,
                "-f", "bestvideo[vcodec^=avc1][ext=mp4]+bestaudio[ext=m4a]/bestvideo[vcodec^=avc1]+bestaudio/best[ext=mp4]/best",
                "--merge-output-format", "mp4",
                "--no-warnings",
                url),
            downloadTimeout(),
            line -> {
                if (progressCallback != null && line.contains("[download]") && line.contains("%")) {
                    Matcher matcher = PERCENT.matcher(line);
                    if (matcher.find()) {
                        float progress = Float.parseFloat(matcher.group(1)) / 100.0f;
                        progressCallback.accept(progress, "下载中 " + line.trim());
                    }
                }
            });

        if (!result.isSuccess()) {
            throw new RuntimeException("yt-dlp 下载失败，退出码: " + result.exitCode());
        }

        Path videoPath = outputDir.resolve("video.mp4");
        if (!Files.exists(videoPath)) {
            try (Stream<Path> paths = Files.list(outputDir)) {
                Optional<Path> found = paths.filter(p -> p.getFileName().toString().startsWith("video.")).findFirst();
                if (found.isPresent()) {
                    videoPath = found.get();
                }
            }
        }

        return ensureH264(videoPath, progressCallback);
    }

    /**
     * 下载前检查视频时长，超过 app.max-video-duration 直接拒绝。
     *
     * <p>时长获取失败时不阻断下载（部分站点不返回 duration），只记录告警。
     */
    private void enforceMaxDuration(String url) {
        int maxSeconds = appProperties.getMaxVideoDuration();
        if (maxSeconds <= 0) {
            return;
        }

        String rawDuration;
        try {
            ProcessRunner.ProcessResult probe = ProcessRunner.run(
                List.of("yt-dlp", "--no-warnings", "--skip-download", "--print", "%(duration)s", url),
                Duration.ofSeconds(90));
            if (!probe.isSuccess()) {
                log.warn("获取视频时长失败（退出码 {}），跳过时长校验", probe.exitCode());
                return;
            }
            rawDuration = probe.output().strip().lines().findFirst().orElse("").strip();
        } catch (Exception e) {
            log.warn("获取视频时长失败，跳过时长校验: {}", e.getMessage());
            return;
        }

        if (rawDuration.isEmpty() || "NA".equalsIgnoreCase(rawDuration)) {
            log.warn("视频时长未知，跳过时长校验");
            return;
        }

        double duration;
        try {
            duration = Double.parseDouble(rawDuration);
        } catch (NumberFormatException e) {
            log.warn("无法解析视频时长 \"{}\"，跳过时长校验", rawDuration);
            return;
        }

        if (duration > maxSeconds) {
            throw new IllegalArgumentException(String.format(
                "视频时长 %d 分 %d 秒，超出上限 %d 秒",
                (int) duration / 60, (int) duration % 60, maxSeconds));
        }
        log.info("视频时长校验通过: {}s（上限 {}s）", (int) duration, maxSeconds);
    }

    private String getVideoCodec(Path videoPath) {
        try {
            ProcessRunner.ProcessResult result = ProcessRunner.run(
                List.of("ffprobe", "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=codec_name",
                    "-of", "csv=p=0",
                    videoPath.toString()),
                Duration.ofSeconds(30));
            return result.output().strip().lines().findFirst().orElse("").strip();
        } catch (Exception e) {
            log.warn("ffprobe 检测视频编码失败: {}", e.getMessage());
            return "";
        }
    }

    private Path ensureH264(Path videoPath, BiConsumer<Float, String> progressCallback) throws Exception {
        String codec = getVideoCodec(videoPath);
        log.info("视频编码: {}, 文件: {}", codec, videoPath);

        if (codec.startsWith("avc1") || codec.startsWith("h264") || codec.startsWith("H264")) {
            return videoPath;
        }

        log.info("视频编码非 H.264 ({}), 开始转码...", codec);
        if (progressCallback != null) {
            progressCallback.accept(0.95f, "视频编码不兼容，正在转码为 H.264...");
        }

        Path transcodedPath = videoPath.resolveSibling("video_h264.mp4");

        ProcessRunner.ProcessResult result = ProcessRunner.run(
            List.of("ffmpeg", "-y",
                "-i", videoPath.toString(),
                "-c:v", "libx264",
                "-preset", "fast",
                "-crf", "23",
                "-c:a", "aac",
                "-movflags", "+faststart",
                transcodedPath.toString()),
            transcodeTimeout());

        if (!result.isSuccess()) {
            log.warn("ffmpeg 转码失败（退出码 {}），使用原始文件", result.exitCode());
            return videoPath;
        }

        Files.move(transcodedPath, videoPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("视频转码完成: {}", videoPath);
        return videoPath;
    }

    /** 下载耗时与网络相关，按视频上限的 4 倍并额外留 10 分钟 */
    private Duration downloadTimeout() {
        return Duration.ofSeconds((long) appProperties.getMaxVideoDuration() * 4 + 600);
    }

    /** 转码为纯 CPU 任务，按视频上限的 6 倍并额外留 10 分钟 */
    private Duration transcodeTimeout() {
        return Duration.ofSeconds((long) appProperties.getMaxVideoDuration() * 6 + 600);
    }
}
