package com.bili.translator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

@Service
public class AudioExtractor {

    public void extract(Path videoPath, Path outputPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y",
            "-i", videoPath.toString(),
            "-ar", "16000",
            "-ac", "1",
            "-f", "wav",
            outputPath.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("ffmpeg 音频提取失败，退出码: " + exitCode);
        }
    }

    public double getDuration(Path audioPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "ffprobe",
            "-v", "quiet",
            "-print_format", "json",
            "-show_format",
            audioPath.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        process.waitFor();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(output.toString());
        return root.path("format").path("duration").asDouble();
    }
}
