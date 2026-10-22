package com.bili.translator.service;

import com.bili.translator.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class FileManager {

    private static final Logger log = LoggerFactory.getLogger(FileManager.class);

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

    /**
     * 清理不属于任何已知任务、且超过保留期的目录。
     *
     * <p>按任务维度的清理由 {@code PipelineProcessor#cleanupExpiredTasks} 负责，
     * 这里只处理「没有任何任务指向」的残留目录（服务中断、元数据损坏等）。
     */
    public void cleanupOrphanDirs(Set<String> knownTaskIds, int maxAgeHours) {
        Path tempDir = Path.of(appProperties.getTempDir());
        if (!Files.exists(tempDir)) {
            return;
        }
        long cutoff = System.currentTimeMillis() - maxAgeHours * 3600_000L;

        try (Stream<Path> dirs = Files.list(tempDir)) {
            dirs.filter(Files::isDirectory)
                .filter(dir -> !knownTaskIds.contains(dir.getFileName().toString()))
                .filter(dir -> lastModifiedMillis(dir) < cutoff)
                .forEach(this::deleteRecursively);
        } catch (IOException e) {
            log.warn("清理残留目录失败: {}", e.getMessage());
        }
    }

    @PostConstruct
    public void init() {
        Path tempDir = Path.of(appProperties.getTempDir());
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new RuntimeException("创建临时目录失败: " + e.getMessage(), e);
        }
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            // 读不到时间时保守处理，不删除
            return Long.MAX_VALUE;
        }
    }

    private void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 文件被占用时跳过，交给下次清理
                }
            });
            log.info("已清理残留目录: {}", dir.getFileName());
        } catch (IOException e) {
            log.warn("删除目录失败: {} - {}", dir, e.getMessage());
        }
    }
}
