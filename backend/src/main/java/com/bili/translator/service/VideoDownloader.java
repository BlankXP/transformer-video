package com.bili.translator.service;

import com.bili.translator.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class VideoDownloader {

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
            "-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
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

        return videoPath;
    }
}
