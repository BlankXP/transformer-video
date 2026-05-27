package com.bili.translator.service;

import com.bili.translator.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class VideoDownloader {

    private static final Logger log = LoggerFactory.getLogger(VideoDownloader.class);

    private final AppProperties appProperties;

    public VideoDownloader(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public Path download(String url, Path outputDir, BiConsumer<Float, String> progressCallback) throws Exception {
        if (url.startsWith("BV") || url.startsWith("bv")) {
            url = "https://www.bilibili.com/video/" + url;
        }

        String outputPath = outputDir.resolve("video.%(ext)s").toString();

        ProcessBuilder pb = new ProcessBuilder(
            "yt-dlp",
            "-o", outputPath,
            "-f", "bestvideo[vcodec^=avc1][ext=mp4]+bestaudio[ext=m4a]/bestvideo[vcodec^=avc1]+bestaudio/best[ext=mp4]/best",
            "--merge-output-format", "mp4",
            "--no-warnings",
            url
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (progressCallback != null && line.contains("[download]") && line.contains("%")) {
                    Pattern pattern = Pattern.compile("(\\d+\\.?\\d*)%");
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        float progress = Float.parseFloat(matcher.group(1)) / 100.0f;
                        progressCallback.accept(progress, "下载中 " + line.trim());
                    }
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("yt-dlp 下载失败，退出码: " + exitCode);
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

    private String getVideoCodec(Path videoPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=codec_name",
                "-of", "csv=p=0",
                videoPath.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String codec = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    codec = line.trim();
                }
            }
            process.waitFor();
            return codec;
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

        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y",
            "-i", videoPath.toString(),
            "-c:v", "libx264",
            "-preset", "fast",
            "-crf", "23",
            "-c:a", "aac",
            "-movflags", "+faststart",
            transcodedPath.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {}
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.warn("ffmpeg 转码失败，使用原始文件");
            return videoPath;
        }

        Files.move(transcodedPath, videoPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("视频转码完成: {}", videoPath);
        return videoPath;
    }
}
