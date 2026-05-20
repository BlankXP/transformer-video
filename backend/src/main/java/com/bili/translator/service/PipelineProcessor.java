package com.bili.translator.service;

import com.bili.translator.config.AppProperties;
import com.bili.translator.model.ProcessRequest;
import com.bili.translator.model.SubtitleEntry;
import com.bili.translator.model.TaskResult;
import com.bili.translator.model.TaskStatus;
import com.bili.translator.util.FileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Service
public class PipelineProcessor {

    private static final Logger log = LoggerFactory.getLogger(PipelineProcessor.class);

    private final ConcurrentHashMap<String, TaskStatus> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TaskResult> results = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BiConsumer<String, String, Double, String>> progressCallbacks = new ConcurrentHashMap<>();

    private final DownloaderService downloaderService;
    private final AudioExtractorService audioExtractorService;
    private final SpeechRecognitionService speechRecognitionService;
    private final TranslatorService translatorService;
    private final SubtitleGeneratorService subtitleGeneratorService;
    private final FileManager fileManager;
    private final AppProperties appProperties;

    public PipelineProcessor(DownloaderService downloaderService,
                             AudioExtractorService audioExtractorService,
                             SpeechRecognitionService speechRecognitionService,
                             TranslatorService translatorService,
                             SubtitleGeneratorService subtitleGeneratorService,
                             FileManager fileManager,
                             AppProperties appProperties) {
        this.downloaderService = downloaderService;
        this.audioExtractorService = audioExtractorService;
        this.speechRecognitionService = speechRecognitionService;
        this.translatorService = translatorService;
        this.subtitleGeneratorService = subtitleGeneratorService;
        this.fileManager = fileManager;
        this.appProperties = appProperties;
    }

    public void registerProgressCallback(String taskId, BiConsumer<String, String, Double, String> callback) {
        progressCallbacks.put(taskId, callback);
    }

    public void unregisterProgressCallback(String taskId) {
        progressCallbacks.remove(taskId);
    }

    private void notifyProgress(String taskId, String stage, double progress, String message) {
        TaskStatus status = tasks.get(taskId);
        if (status != null) {
            status.setStage(stage);
            status.setProgress(progress);
            status.setMessage(message);
        }
        BiConsumer<String, String, Double, String> callback = progressCallbacks.get(taskId);
        if (callback != null) {
            try {
                callback.accept(taskId, stage, progress, message);
            } catch (Exception e) {
                log.warn("Progress callback error: {}", e.getMessage());
            }
        }
    }

    public String createTask(ProcessRequest request) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        TaskStatus status = new TaskStatus(taskId, "downloading", 0.0, "任务已创建");
        tasks.put(taskId, status);
        return taskId;
    }

    public TaskStatus getTaskStatus(String taskId) {
        return tasks.get(taskId);
    }

    public TaskResult getTaskResult(String taskId) {
        return results.get(taskId);
    }

    public void process(String taskId, ProcessRequest request) {
        try {
            Path taskDir = fileManager.createTaskDir(taskId);
            Path videoPath = taskDir.resolve("video.mp4");
            Path audioPath = taskDir.resolve("audio.wav");
            Path srtPath = taskDir.resolve("subtitles.srt");

            notifyProgress(taskId, "downloading", 0.0, "开始下载视频");
            String finalTaskId = taskId;
            downloaderService.download(request.getUrl(), taskDir, (p, m) ->
                    notifyProgress(finalTaskId, "downloading", 0.0 + p * 0.25, m));
            notifyProgress(taskId, "downloading", 0.25, "视频下载完成");

            notifyProgress(taskId, "extracting_audio", 0.25, "开始提取音频");
            audioExtractorService.extract(videoPath, audioPath);
            notifyProgress(taskId, "extracting_audio", 0.30, "音频提取完成");

            notifyProgress(taskId, "recognizing", 0.30, "开始语音识别");
            speechRecognitionService.setProgressCallback((p, m) ->
                    notifyProgress(finalTaskId, "recognizing", 0.30 + p * 0.35, m));
            List<SpeechRecognitionService.RecognitionItem> recognized =
                    speechRecognitionService.recognize(audioPath, request.getSourceLanguage());
            notifyProgress(taskId, "recognizing", 0.65, "语音识别完成");

            notifyProgress(taskId, "translating", 0.65, "开始翻译字幕");
            List<TranslatorService.RecognizedItem> recognizedItems = recognized.stream()
                    .map(r -> new TranslatorService.RecognizedItem(r.text(), r.startTime(), r.endTime()))
                    .toList();
            translatorService.setProgressCallback((p, m) ->
                    notifyProgress(finalTaskId, "translating", 0.65 + p * 0.25, m));
            List<TranslatorService.TranslatedItem> translated =
                    translatorService.translate(recognizedItems, request.getSourceLanguage(), request.getTargetLanguage());
            notifyProgress(taskId, "translating", 0.90, "字幕翻译完成");

            notifyProgress(taskId, "generating_subtitle", 0.90, "开始生成字幕文件");
            List<SubtitleEntry> subtitleEntries = new java.util.ArrayList<>();
            for (int i = 0; i < translated.size(); i++) {
                TranslatorService.TranslatedItem item = translated.get(i);
                subtitleEntries.add(new SubtitleEntry(
                        i + 1,
                        SubtitleGeneratorService.secondsToSrtTime(item.startTime),
                        SubtitleGeneratorService.secondsToSrtTime(item.endTime),
                        item.text,
                        item.translatedText
                ));
            }
            subtitleGeneratorService.generateSrt(subtitleEntries, srtPath, true);
            notifyProgress(taskId, "generating_subtitle", 1.00, "字幕文件生成完成");

            double duration = audioExtractorService.getDuration(audioPath);
            TaskResult result = new TaskResult();
            result.setVideoPath(videoPath.toString());
            result.setSrtPath(srtPath.toString());
            result.setSubtitles(subtitleEntries);
            result.setDuration(duration);
            results.put(taskId, result);
            tasks.get(taskId).setResult(result);
            notifyProgress(taskId, "completed", 1.0, "处理完成");

        } catch (Exception e) {
            log.error("Pipeline processing failed for task {}: {}", taskId, e.getMessage(), e);
            notifyProgress(taskId, "failed", 0.0, "处理失败: " + e.getMessage());
        }
    }

    public boolean updateSubtitles(String taskId, List<SubtitleEntry> subtitles) {
        TaskResult result = results.get(taskId);
        if (result == null) return false;
        result.setSubtitles(subtitles);
        try {
            Path taskDir = fileManager.createTaskDir(taskId);
            Path srtPath = taskDir.resolve("subtitles.srt");
            subtitleGeneratorService.generateSrt(subtitles, srtPath, true);
            result.setSrtPath(srtPath.toString());
            return true;
        } catch (Exception e) {
            log.error("Failed to update subtitles for task {}: {}", taskId, e.getMessage());
            return false;
        }
    }
}
