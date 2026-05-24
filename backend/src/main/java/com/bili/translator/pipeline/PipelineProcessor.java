package com.bili.translator.pipeline;

import com.bili.translator.config.AppProperties;
import com.bili.translator.model.*;
import com.bili.translator.service.*;
import com.bili.translator.websocket.ProgressWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PipelineProcessor {

    private static final Logger log = LoggerFactory.getLogger(PipelineProcessor.class);

    private final ConcurrentHashMap<String, TaskStatus> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TaskResult> results = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProgressCallback> progressCallbacks = new ConcurrentHashMap<>();

    private final AppProperties appProperties;
    private final FileManager fileManager;
    private final VideoDownloader videoDownloader;
    private final AudioExtractor audioExtractor;
    private final SpeechRecognitionService speechRecognitionService;
    private final TranslatorService translatorService;
    private final SubtitleGenerator subtitleGenerator;
    private final SubtitleBurner subtitleBurner;
    private final ProgressWebSocketHandler webSocketHandler;

    public PipelineProcessor(AppProperties appProperties, FileManager fileManager,
                              VideoDownloader videoDownloader, AudioExtractor audioExtractor,
                              SpeechRecognitionService speechRecognitionService,
                              TranslatorService translatorService,
                              SubtitleGenerator subtitleGenerator,
                              SubtitleBurner subtitleBurner,
                              ProgressWebSocketHandler webSocketHandler) {
        this.appProperties = appProperties;
        this.fileManager = fileManager;
        this.videoDownloader = videoDownloader;
        this.audioExtractor = audioExtractor;
        this.speechRecognitionService = speechRecognitionService;
        this.translatorService = translatorService;
        this.subtitleGenerator = subtitleGenerator;
        this.subtitleBurner = subtitleBurner;
        this.webSocketHandler = webSocketHandler;
    }

    public String createTask(ProcessRequest request) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setStage("downloading");
        status.setProgress(0.0);
        status.setMessage("任务已创建");
        tasks.put(taskId, status);
        return taskId;
    }

    public TaskStatus getTaskStatus(String taskId) {
        return tasks.get(taskId);
    }

    public TaskResult getTaskResult(String taskId) {
        return results.get(taskId);
    }

    public void registerProgressCallback(String taskId, ProgressCallback callback) {
        progressCallbacks.put(taskId, callback);
    }

    public void unregisterProgressCallback(String taskId) {
        progressCallbacks.remove(taskId);
    }

    private void notifyProgress(String taskId, String stage, float progress, String message) {
        TaskStatus status = tasks.get(taskId);
        if (status != null) {
            status.setStage(stage);
            status.setProgress(progress);
            status.setMessage(message);
        }

        ProgressCallback callback = progressCallbacks.get(taskId);
        if (callback != null) {
            try {
                callback.accept(taskId, stage, progress, message);
            } catch (Exception ignored) {}
        }

        webSocketHandler.sendProgress(taskId, stage, progress, message);
    }

    @Async
    public void process(String taskId, ProcessRequest request) {
        try {
            Path taskDir = fileManager.createTaskDir(taskId);
            Path videoPath = taskDir.resolve("video.mp4");
            Path audioPath = taskDir.resolve("audio.wav");
            Path srtPath = taskDir.resolve("subtitles.srt");

            notifyProgress(taskId, "downloading", 0.0f, "开始下载视频");
            Path downloadedPath = videoDownloader.download(request.getUrl(), taskDir,
                (p, m) -> notifyProgress(taskId, "downloading", 0.0f + p * 0.25f, m));
            if (!downloadedPath.equals(videoPath)) {
                Files.move(downloadedPath, videoPath, StandardCopyOption.REPLACE_EXISTING);
            }
            notifyProgress(taskId, "downloading", 0.25f, "视频下载完成");

            notifyProgress(taskId, "extracting_audio", 0.25f, "开始提取音频");
            audioExtractor.extract(videoPath, audioPath);
            notifyProgress(taskId, "extracting_audio", 0.30f, "音频提取完成");

            notifyProgress(taskId, "recognizing", 0.30f, "开始语音识别");
            List<SpeechRecognitionService.RecognizedItem> recognized =
                speechRecognitionService.recognize(audioPath, request.getSourceLanguage(),
                    (p, m) -> notifyProgress(taskId, "recognizing", 0.30f + p * 0.35f, m));
            notifyProgress(taskId, "recognizing", 0.65f, "语音识别完成");

            Path recognizedTextPath = taskDir.resolve("recognized.txt");
            try (BufferedWriter writer = Files.newBufferedWriter(recognizedTextPath)) {
                for (SpeechRecognitionService.RecognizedItem item : recognized) {
                    writer.write(item.getText());
                    writer.newLine();
                }
            }
            log.info("语音识别文本已输出到: {}", recognizedTextPath);

            notifyProgress(taskId, "translating", 0.65f, "开始翻译字幕");
            List<TranslatorService.TranslatedItem> translated =
                translatorService.translate(recognized, request.getSourceLanguage(), request.getTargetLanguage(),
                    (p, m) -> notifyProgress(taskId, "translating", 0.65f + p * 0.25f, m));
            notifyProgress(taskId, "translating", 0.90f, "字幕翻译完成");

            notifyProgress(taskId, "generating_subtitle", 0.90f, "开始生成字幕文件");
            List<SubtitleEntry> subtitleEntries = new ArrayList<>();
            for (int i = 0; i < translated.size(); i++) {
                TranslatorService.TranslatedItem item = translated.get(i);
                SubtitleEntry entry = new SubtitleEntry();
                entry.setIndex(i + 1);
                entry.setStartTime(SubtitleGenerator.secondsToSrtTime(item.getStartTime()));
                entry.setEndTime(SubtitleGenerator.secondsToSrtTime(item.getEndTime()));
                entry.setSourceText(item.getText());
                entry.setTranslatedText(item.getTranslatedText());
                subtitleEntries.add(entry);
            }
            subtitleGenerator.generateSrt(subtitleEntries, srtPath, true);
            notifyProgress(taskId, "generating_subtitle", 0.92f, "字幕文件生成完成");

            Path burnedVideoPath = taskDir.resolve("video_burned.mp4");
            notifyProgress(taskId, "burning_subtitle", 0.92f, "开始烧录字幕到视频");
            try {
                subtitleBurner.burn(videoPath, srtPath, burnedVideoPath);
                notifyProgress(taskId, "burning_subtitle", 0.98f, "字幕烧录完成");
            } catch (Exception e) {
                log.warn("字幕烧录失败，跳过烧录步骤: {}", e.getMessage());
                burnedVideoPath = null;
                notifyProgress(taskId, "burning_subtitle", 0.98f, "字幕烧录失败已跳过");
            }

            double duration = audioExtractor.getDuration(audioPath);
            TaskResult result = new TaskResult();
            result.setVideoPath(videoPath.toString());
            result.setSrtPath(srtPath.toString());
            result.setRecognizedTextPath(recognizedTextPath.toString());
            if (burnedVideoPath != null) {
                result.setBurnedVideoPath(burnedVideoPath.toString());
            }
            result.setSubtitles(subtitleEntries);
            result.setDuration(duration);
            results.put(taskId, result);

            TaskStatus status = tasks.get(taskId);
            if (status != null) {
                status.setResult(result);
            }
            notifyProgress(taskId, "completed", 1.0f, "处理完成");

        } catch (Exception e) {
            notifyProgress(taskId, "failed", 0.0f, "处理失败: " + e.getMessage());
        }
    }

    public boolean updateSubtitles(String taskId, List<SubtitleEntry> subtitles) {
        TaskResult result = results.get(taskId);
        if (result == null) return false;

        result.setSubtitles(subtitles);
        Path taskDir = fileManager.createTaskDir(taskId);
        Path srtPath = taskDir.resolve("subtitles.srt");
        try {
            subtitleGenerator.generateSrt(subtitles, srtPath, true);
            result.setSrtPath(srtPath.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
