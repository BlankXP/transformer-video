package com.bili.translator;

import com.bili.translator.util.FileManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;

@SpringBootApplication
@EnableScheduling
public class BiliTranslatorApplication {

    private FileManager fileManager;

    public BiliTranslatorApplication(FileManager fileManager) {
        this.fileManager = fileManager;
    }

    public static void main(String[] args) {
        SpringApplication.run(BiliTranslatorApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("ffmpeg 未安装或不可用，请先安装 ffmpeg");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("ffmpeg 未安装，请先安装 ffmpeg");
        }
        fileManager.cleanupExpiredTasks(24);
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanupTask() {
        fileManager.cleanupExpiredTasks(24);
    }
}
