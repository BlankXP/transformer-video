package com.bili.translator.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class DownloaderService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DownloaderService.class);

    private static final Pattern PROGRESS_PATTERN = Pattern.compile("\\[download]\\s+([\\d.]+)%");

    public Path download(String url, Path outputDir, BiConsumer<Double, String> progressCallback) {
        String resolvedUrl = url;
        if (url.startsWith("BV") || url.startsWith("bv")) {
            resolvedUrl = "https://www.bilibili.com/video/" + url;
        }

        String outputTemplate = outputDir.resolve("video.%(ext)s").toString();
        ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "-o", outputTemplate,
                "-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
                "--merge-output-format", "mp4",
                "--quiet", "--no-warnings",
                resolvedUrl
        );
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = PROGRESS_PATTERN.matcher(line);
                    if (matcher.find()) {
                        double progress = Double.parseDouble(matcher.group(1));
                        progressCallback.accept(progress, line.trim());
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("yt-dlp exited with code " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to download video", e);
        }

        Path videoFile = findVideoFile(outputDir);
        if (videoFile == null) {
            throw new RuntimeException("No video file found in output directory");
        }

        if (!videoFile.toString().endsWith(".mp4")) {
            Path mp4Path = outputDir.resolve("video.mp4");
            try {
                videoFile = Files.move(videoFile, mp4Path);
            } catch (IOException e) {
                throw new RuntimeException("Failed to rename video file to mp4", e);
            }
        }

        return videoFile;
    }

    private Path findVideoFile(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    String name = entry.getFileName().toString().toLowerCase();
                    if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm")
                            || name.endsWith(".flv") || name.endsWith(".avi") || name.endsWith(".mov")) {
                        return entry;
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to list files in output directory", e);
        }
        return null;
    }
}
