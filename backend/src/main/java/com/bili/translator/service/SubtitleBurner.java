package com.bili.translator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

@Service
public class SubtitleBurner {

    private static final Logger log = LoggerFactory.getLogger(SubtitleBurner.class);

    public void burn(Path videoPath, Path srtPath, Path outputPath) throws Exception {
        String srtPathEscaped = escapePath(srtPath.toString());

        String filter = "subtitles=" + srtPathEscaped
            + ":force_style='FontName=Noto Sans CJK SC,FontSize=24,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,Outline=2,MarginV=30'";

        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y",
            "-i", videoPath.toString(),
            "-vf", filter,
            "-c:v", "libx264",
            "-preset", "fast",
            "-crf", "23",
            "-c:a", "copy",
            "-movflags", "+faststart",
            outputPath.toString()
        );
        pb.redirectErrorStream(true);
        log.info("开始烧录字幕: video={}, srt={}, output={}", videoPath, srtPath, outputPath);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("ffmpeg 字幕烧录失败，退出码: {}, 输出: {}", exitCode, output);
            throw new RuntimeException("ffmpeg 字幕烧录失败，退出码: " + exitCode);
        }

        log.info("字幕烧录完成: {}", outputPath);
    }

    private String escapePath(String path) {
        String escaped = path.replace("\\", "/");
        escaped = escaped.replace(":", "\\:");
        escaped = escaped.replace("'", "\\'");
        escaped = escaped.replace("[", "\\[");
        escaped = escaped.replace("]", "\\]");
        return escaped;
    }
}
