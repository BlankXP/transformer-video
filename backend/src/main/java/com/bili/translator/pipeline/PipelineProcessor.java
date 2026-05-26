package com.bili.translator.pipeline;

import com.bili.translator.config.AppProperties;
import com.bili.translator.model.*;
import com.bili.translator.service.*;
import com.bili.translator.websocket.ProgressWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PipelineProcessor {

    private static final Logger log = LoggerFactory.getLogger(PipelineProcessor.class);
    private static final String COMPLETED_STEPS_SEP = ",";
    private static final String META_FILE = "task_meta.json";

    private final ConcurrentHashMap<String, TaskStatus> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProgressCallback> progressCallbacks = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @PostConstruct
    public void init() {
        loadPersistedTasks();
    }

    private void loadPersistedTasks() {
        Path tempDir = Path.of(appProperties.getTempDir());
        if (!Files.exists(tempDir)) return;

        try (var dirs = Files.list(tempDir)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                Path metaFile = dir.resolve(META_FILE);
                if (Files.exists(metaFile)) {
                    try {
                        String json = Files.readString(metaFile);
                        TaskStatus status = objectMapper.readValue(json, TaskStatus.class);
                        if (status != null && status.getTaskId() != null) {
                            if ("failed".equals(status.getStage())) {
                                tasks.put(status.getTaskId(), status);
                            } else if (!"completed".equals(status.getStage())) {
                                status.setStage("failed");
                                status.setMessage("服务重启，任务中断");
                                persistTask(status.getTaskId());
                                tasks.put(status.getTaskId(), status);
                            } else {
                                tasks.put(status.getTaskId(), status);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("加载任务元数据失败: {}", dir.getFileName(), e);
                    }
                }
            });
        } catch (Exception e) {
            log.warn("扫描任务目录失败", e);
        }
    }

    public void persistTask(String taskId) {
        TaskStatus status = tasks.get(taskId);
        if (status == null) return;

        try {
            Path taskDir = fileManager.getTaskDir(taskId);
            Files.createDirectories(taskDir);
            Path metaFile = taskDir.resolve(META_FILE);
            objectMapper.writeValue(metaFile.toFile(), status);
        } catch (Exception e) {
            log.warn("持久化任务元数据失败: {}", taskId, e);
        }
    }

    public String createTask(ProcessRequest request) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setStage("downloading");
        status.setProgress(0.0);
        status.setMessage("任务已创建");
        status.setRequest(request);
        status.setCreatedAt(System.currentTimeMillis());
        tasks.put(taskId, status);
        persistTask(taskId);
        return taskId;
    }

    public TaskStatus getTaskStatus(String taskId) {
        return tasks.get(taskId);
    }

    public TaskResult getTaskResult(String taskId) {
        TaskStatus status = tasks.get(taskId);
        return status != null ? status.getResult() : null;
    }

    public List<TaskStatus> getAllTasks() {
        List<TaskStatus> list = new ArrayList<>(tasks.values());
        list.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return list;
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

    private Set<String> getCompletedSteps(String taskId) {
        TaskStatus status = tasks.get(taskId);
        if (status == null || status.getResult() == null || status.getResult().getCompletedSteps() == null) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(status.getResult().getCompletedSteps().split(COMPLETED_STEPS_SEP)));
    }

    private void markStepCompleted(String taskId, String step) {
        TaskStatus status = tasks.get(taskId);
        if (status == null) return;
        if (status.getResult() == null) {
            status.setResult(new TaskResult());
        }
        Set<String> steps = getCompletedSteps(taskId);
        steps.add(step);
        status.getResult().setCompletedSteps(String.join(COMPLETED_STEPS_SEP, steps));
        persistTask(taskId);
    }

    @Async
    public void process(String taskId, ProcessRequest request) {
        Set<String> completed = getCompletedSteps(taskId);
        Path taskDir = fileManager.createTaskDir(taskId);
        Path videoPath = taskDir.resolve("video.mp4");
        Path audioPath = taskDir.resolve("audio.wav");
        Path recognizedTextPath = taskDir.resolve("recognized.txt");
        Path srtPath = taskDir.resolve("subtitles.srt");
        Path burnedVideoPath = taskDir.resolve("video_burned.mp4");

        try {
            if (!completed.contains("downloading")) {
                notifyProgress(taskId, "downloading", 0.0f, "开始下载视频");
                Path downloadedPath = videoDownloader.download(request.getUrl(), taskDir,
                    (p, m) -> notifyProgress(taskId, "downloading", p * (request.isTranslateSubtitles() ? 0.25f : 0.95f), m));
                if (!downloadedPath.equals(videoPath)) {
                    Files.move(downloadedPath, videoPath, StandardCopyOption.REPLACE_EXISTING);
                }
                notifyProgress(taskId, "downloading", request.isTranslateSubtitles() ? 0.25f : 0.95f, "视频下载完成");

                TaskStatus status = tasks.get(taskId);
                if (status.getResult() == null) status.setResult(new TaskResult());
                status.getResult().setVideoPath(videoPath.toString());
                markStepCompleted(taskId, "downloading");
            }

            if (!request.isTranslateSubtitles()) {
                TaskStatus status = tasks.get(taskId);
                if (status.getResult() == null) status.setResult(new TaskResult());
                notifyProgress(taskId, "completed", 1.0f, "视频下载完成");
                persistTask(taskId);
                return;
            }

            if (!completed.contains("extracting_audio")) {
                notifyProgress(taskId, "extracting_audio", 0.25f, "开始提取音频");
                audioExtractor.extract(videoPath, audioPath);
                notifyProgress(taskId, "extracting_audio", 0.30f, "音频提取完成");

                TaskStatus status = tasks.get(taskId);
                status.getResult().setAudioPath(audioPath.toString());
                markStepCompleted(taskId, "extracting_audio");
            }

            List<SpeechRecognitionService.RecognizedItem> recognized = null;

            if (!completed.contains("recognizing")) {
                notifyProgress(taskId, "recognizing", 0.30f, "开始语音识别");
                recognized = speechRecognitionService.recognize(audioPath, request.getSourceLanguage(),
                    (p, m) -> notifyProgress(taskId, "recognizing", 0.30f + p * 0.35f, m));
                notifyProgress(taskId, "recognizing", 0.65f, "语音识别完成");

                try (BufferedWriter writer = Files.newBufferedWriter(recognizedTextPath)) {
                    for (SpeechRecognitionService.RecognizedItem item : recognized) {
                        writer.write(item.getText());
                        writer.newLine();
                    }
                }

                TaskStatus status = tasks.get(taskId);
                status.getResult().setRecognizedTextPath(recognizedTextPath.toString());
                markStepCompleted(taskId, "recognizing");
            }

            if (!completed.contains("translating")) {
                if (recognized == null) {
                    recognized = loadRecognizedItems(recognizedTextPath);
                }

                notifyProgress(taskId, "translating", 0.65f, "开始翻译字幕");
                List<TranslatorService.TranslatedItem> translated =
                    translatorService.translate(recognized, request.getSourceLanguage(), request.getTargetLanguage(),
                        (p, m) -> notifyProgress(taskId, "translating", 0.65f + p * 0.20f, m));
                notifyProgress(taskId, "translating", 0.85f, "字幕翻译完成");

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

                TaskStatus status = tasks.get(taskId);
                status.getResult().setSubtitles(subtitleEntries);
                markStepCompleted(taskId, "translating");
            }

            if (!completed.contains("generating_subtitle")) {
                TaskStatus status = tasks.get(taskId);
                List<SubtitleEntry> subtitleEntries = status.getResult().getSubtitles();

                notifyProgress(taskId, "generating_subtitle", 0.85f, "开始生成字幕文件");
                subtitleGenerator.generateSrt(subtitleEntries, srtPath, true);
                notifyProgress(taskId, "generating_subtitle", 0.90f, "字幕文件生成完成");

                status.getResult().setSrtPath(srtPath.toString());
                markStepCompleted(taskId, "generating_subtitle");
            }

            if (!completed.contains("burning_subtitle")) {
                TaskStatus status = tasks.get(taskId);

                notifyProgress(taskId, "burning_subtitle", 0.90f, "开始烧录字幕到视频");
                try {
                    subtitleBurner.burn(videoPath, srtPath, burnedVideoPath);
                    notifyProgress(taskId, "burning_subtitle", 0.98f, "字幕烧录完成");
                    status.getResult().setBurnedVideoPath(burnedVideoPath.toString());
                } catch (Exception e) {
                    log.warn("字幕烧录失败，跳过烧录步骤: {}", e.getMessage());
                    notifyProgress(taskId, "burning_subtitle", 0.98f, "字幕烧录失败已跳过");
                }
                markStepCompleted(taskId, "burning_subtitle");
            }

            TaskStatus status = tasks.get(taskId);
            TaskResult result = status.getResult();
            if (Files.exists(audioPath)) {
                result.setDuration(audioExtractor.getDuration(audioPath));
            }
            notifyProgress(taskId, "completed", 1.0f, "处理完成");
            persistTask(taskId);

        } catch (Exception e) {
            log.error("任务处理失败: {}", taskId, e);
            notifyProgress(taskId, "failed", 0.0f, "处理失败: " + e.getMessage());
            persistTask(taskId);
        }
    }

    private List<SpeechRecognitionService.RecognizedItem> loadRecognizedItems(Path recognizedTextPath) {
        List<SpeechRecognitionService.RecognizedItem> items = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(recognizedTextPath);
            for (String line : lines) {
                if (!line.isBlank()) {
                    SpeechRecognitionService.RecognizedItem item = new SpeechRecognitionService.RecognizedItem();
                    item.setText(line.trim());
                    item.setStartTime(0.0);
                    item.setEndTime(0.0);
                    items.add(item);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("加载识别文本失败: " + e.getMessage(), e);
        }
        return items;
    }

    public boolean updateSubtitles(String taskId, List<SubtitleEntry> subtitles) {
        TaskStatus status = tasks.get(taskId);
        if (status == null || status.getResult() == null) return false;

        status.getResult().setSubtitles(subtitles);
        Path taskDir = fileManager.createTaskDir(taskId);
        Path srtPath = taskDir.resolve("subtitles.srt");
        try {
            subtitleGenerator.generateSrt(subtitles, srtPath, true);
            status.getResult().setSrtPath(srtPath.toString());
            persistTask(taskId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
