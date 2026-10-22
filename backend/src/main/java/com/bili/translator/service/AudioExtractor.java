package com.bili.translator.service;

import com.bili.translator.config.AppProperties;
import com.bili.translator.util.ProcessRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Service
public class AudioExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AppProperties appProperties;

    public AudioExtractor(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public void extract(Path videoPath, Path outputPath) throws Exception {
        ProcessRunner.ProcessResult result = ProcessRunner.run(
            List.of("ffmpeg", "-y",
                "-i", videoPath.toString(),
                "-ar", "16000",
                "-ac", "1",
                "-f", "wav",
                outputPath.toString()),
            extractTimeout());

        if (!result.isSuccess()) {
            throw new RuntimeException("ffmpeg 音频提取失败，退出码: " + result.exitCode());
        }
    }

    public double getDuration(Path audioPath) throws Exception {
        ProcessRunner.ProcessResult result = ProcessRunner.run(
            List.of("ffprobe",
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                audioPath.toString()),
            Duration.ofSeconds(30));

        JsonNode root = MAPPER.readTree(result.output());
        return root.path("format").path("duration").asDouble();
    }

    /** 解码整条音轨，预留 2 倍视频上限 + 10 分钟余量 */
    private Duration extractTimeout() {
        return Duration.ofSeconds((long) appProperties.getMaxVideoDuration() * 2 + 600);
    }
}
