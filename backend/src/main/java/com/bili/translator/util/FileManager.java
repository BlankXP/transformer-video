package com.bili.translator.util;

import com.bili.translator.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Component
public class FileManager {

    private final AppProperties appProperties;

    public FileManager(AppProperties appProperties) {
        this.appProperties = appProperties;
        ensureTempDir();
    }

    private void ensureTempDir() {
        Path tempDir = Paths.get(appProperties.getTempDir());
        tempDir.toFile().mkdirs();
    }

    public Path createTaskDir(String taskId) {
        Path taskDir = Paths.get(appProperties.getTempDir(), taskId);
        taskDir.toFile().mkdirs();
        return taskDir;
    }

    public Path getTaskDir(String taskId) {
        return Paths.get(appProperties.getTempDir(), taskId);
    }

    public void cleanupExpiredTasks(int maxAgeHours) {
        File tempDir = Paths.get(appProperties.getTempDir()).toFile();
        if (!tempDir.exists() || !tempDir.isDirectory()) return;

        long now = System.currentTimeMillis();
        long maxAgeMillis = maxAgeHours * 3600L * 1000L;

        File[] dirs = tempDir.listFiles(File::isDirectory);
        if (dirs == null) return;

        for (File dir : dirs) {
            if (now - dir.lastModified() > maxAgeMillis) {
                deleteDirectory(dir);
            }
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
