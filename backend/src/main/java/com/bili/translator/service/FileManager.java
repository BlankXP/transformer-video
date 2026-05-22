package com.bili.translator.service;

import com.bili.translator.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@Service
public class FileManager {

    private final AppProperties appProperties;

    public FileManager(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public Path createTaskDir(String taskId) {
        Path taskDir = getTaskDir(taskId);
        try {
            Files.createDirectories(taskDir);
        } catch (IOException e) {
            throw new RuntimeException("创建任务目录失败: " + e.getMessage(), e);
        }
        return taskDir;
    }

    public Path getTaskDir(String taskId) {
        return Path.of(appProperties.getTempDir()).resolve(taskId);
    }

    public void cleanupExpiredTasks(int maxAgeHours) {
        Path tempDir = Path.of(appProperties.getTempDir());
        if (!Files.exists(tempDir)) return;

        long now = System.currentTimeMillis();
        long maxAgeMillis = maxAgeHours * 3600L * 1000L;

        try (Stream<Path> paths = Files.list(tempDir)) {
            paths.filter(Files::isDirectory)
                 .filter(dir -> {
                     try {
                         long mtime = Files.getLastModifiedTime(dir).toMillis();
                         return now - mtime > maxAgeMillis;
                     } catch (IOException e) {
                         return false;
                     }
                 })
                 .forEach(dir -> {
                     try {
                         Files.walk(dir)
                              .sorted(Comparator.reverseOrder())
                              .forEach(p -> {
                                  try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                              });
                     } catch (IOException ignored) {}
                 });
        } catch (IOException ignored) {}
    }

    @PostConstruct
    public void init() {
        Path tempDir = Path.of(appProperties.getTempDir());
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new RuntimeException("创建临时目录失败: " + e.getMessage(), e);
        }
        cleanupExpiredTasks(24);
    }
}
