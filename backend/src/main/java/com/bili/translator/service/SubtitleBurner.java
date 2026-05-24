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
        String srtPathEscaped = srtPath.toString().replace("\\", "/").replace(":", "\\:");

        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y",
            "-i", videoPath.toString(),
            "-vf", "subtitles='" + srtPathEscaped + "'",
            "-c:a", "copy",
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
}
