package com.bili.translator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;

@Service
public class AudioExtractorService {

    private static final Logger log = LoggerFactory.getLogger(AudioExtractorService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Path extract(Path videoPath, Path outputPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", videoPath.toString(),
                    "-ar", "16000", "-ac", "1", "-f", "wav", outputPath.toString()
            );
            pb.redirectErrorStream(true);
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
                throw new RuntimeException("ffmpeg exited with code " + exitCode + ": " + output);
            }
            return outputPath;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract audio", e);
        }
    }

    public double getDuration(Path audioPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "quiet", "-print_format", "json",
                    "-show_format", audioPath.toString()
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
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("ffprobe exited with code " + exitCode);
            }
            JsonNode root = objectMapper.readTree(output.toString());
            JsonNode durationNode = root.path("format").path("duration");
            if (durationNode.isMissingNode()) {
                throw new RuntimeException("Duration not found in ffprobe output");
            }
            return durationNode.asDouble();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get audio duration", e);
        }
    }
}
