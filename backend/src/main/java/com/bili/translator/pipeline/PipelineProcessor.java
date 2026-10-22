package com.bili.translator.pipeline;

import com.bili.translator.config.AppProperties;
import com.bili.translator.model.*;
import com.bili.translator.service.*;
import com.bili.translator.websocket.ProgressWebSocketHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class PipelineProcessor {

    private static final Logger log = LoggerFactory.getLogger(PipelineProcessor.class);
    private static final String COMPLETED_STEPS_SEP = ",";
    private static final String META_FILE = "task_meta.json";

    private final ConcurrentHashMap<String, TaskStatus> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProgressCallback> progressCallbacks = new ConcurrentHashMap<>();
    /** 任务键（URL + 是否翻译 + 目标语言）-> taskId，用于原子查重 */
    private final ConcurrentHashMap<String, String> taskKeyIndex = new ConcurrentHashMap<>();
    /** 正在执行的 taskId，删除时用于拒绝 */
    private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();
    private final Object createLock = new Object();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

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
                                registerTask(status);
                            } else if (!"completed".equals(status.getStage())) {
                                status.setStage("failed");
                                status.setMessage("服务重启，任务中断");
                                registerTask(status);
                                persistTask(status.getTaskId());
                            } else {
                                registerTask(status);
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

    private void registerTask(TaskStatus status) {
        tasks.put(status.getTaskId(), status);
        String key = buildTaskKey(status.getRequest());
        if (key != null) {
            taskKeyIndex.put(key, status.getTaskId());
        }
    }

    public String createTask(ProcessRequest request) {
        String taskId = UUID.randomUUID().toString();
        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setStage("downloading");
        status.setProgress(0.0);
        status.setMessage("任务已创建");
        status.setRequest(request);
        status.setCreatedAt(System.currentTimeMillis());
        registerTask(status);
        persistTask(taskId);
        return taskId;
    }

    /**
     * 原子地「查重 + 创建」。
     *
     * <p>任务键包含是否翻译与目标语言，避免「先仅下载、后请求翻译」或换目标语言时复用旧任务；
     * 查重与创建在同一把锁内完成，并发提交不会产生重复任务。
     */
    public CreateResult createOrGetTask(ProcessRequest request) {
        String key = buildTaskKey(request);
        synchronized (createLock) {
            if (key != null) {
                String existingId = taskKeyIndex.get(key);
                if (existingId != null) {
                    TaskStatus existing = tasks.get(existingId);
                    if (existing != null) {
                        return new CreateResult(existingId, true);
                    }
                    // 索引悬挂（任务已被删除），清理后重新创建
                    taskKeyIndex.remove(key, existingId);
                }
            }
            return new CreateResult(createTask(request), false);
        }
    }

    /**
     * 任务键：归一化 URL + 是否翻译 + 目标语言。
     */
    private String buildTaskKey(ProcessRequest request) {
        if (request == null) {
            return null;
        }
        String url = normalizeUrl(request.getUrl());
        if (url.isEmpty()) {
            return null;
        }
        if (!request.isTranslateSubtitles()) {
            return url + "|download|-";
        }
        String lang = request.getTargetLanguage();
        return url + "|translate|" + (lang == null || lang.isBlank() ? "auto" : lang.toLowerCase());
    }

    public record CreateResult(String taskId, boolean alreadyExists) {
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

    public TaskStatus findByUrl(String url) {
        String normalized = normalizeUrl(url);
        for (TaskStatus status : tasks.values()) {
            if (status.getRequest() != null) {
                String existingNormalized = normalizeUrl(status.getRequest().getUrl());
                if (normalized.equals(existingNormalized)) {
                    return status;
                }
            }
        }
        return null;
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) return "";

        Pattern bvPattern = Pattern.compile("(BV[a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher bvMatcher = bvPattern.matcher(url);
        if (bvMatcher.find()) {
            return bvMatcher.group(1).toUpperCase();
        }

        Pattern avPattern = Pattern.compile("av(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher avMatcher = avPattern.matcher(url);
        if (avMatcher.find()) {
            return "AV" + avMatcher.group(1);
        }

        String normalized = url.split("[?]")[0];
        normalized = normalized.replaceAll("/+$", "");
        normalized = normalized.replaceFirst("^https?://(www\\.)?", "");
        return normalized;
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
        runningTasks.add(taskId);
        try {
            runPipeline(taskId, request);
        } finally {
            runningTasks.remove(taskId);
        }
    }

    private void runPipeline(String taskId, ProcessRequest request) {
        Set<String> completed = getCompletedSteps(taskId);
        Path taskDir = fileManager.createTaskDir(taskId);
        Path videoPath = taskDir.resolve("video.mp4");
        Path audioPath = taskDir.resolve("audio.wav");
        Path recognizedTextPath = taskDir.resolve("recognized.txt");
        Path recognizedJsonPath = taskDir.resolve("recognized.json");
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
                recognized = speechRecognitionService.recognize(audioPath,
                    (p, m) -> notifyProgress(taskId, "recognizing", 0.30f + p * 0.35f, m));
                notifyProgress(taskId, "recognizing", 0.65f, "语音识别完成");

                // 完整结果（含时间戳）落盘，供重试时恢复；纯文本仅用于前端下载
                objectMapper.writeValue(recognizedJsonPath.toFile(), recognized);
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
                    recognized = loadRecognizedItems(recognizedJsonPath, recognizedTextPath, audioPath);
                }

                notifyProgress(taskId, "translating", 0.65f, "开始翻译字幕");
                List<TranslatorService.TranslatedItem> translated =
                    translatorService.translate(recognized, request.getTargetLanguage(),
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

    /**
     * 加载识别结果。
     *
     * <p>优先读取 recognized.json（含完整时间戳）；仅当历史任务缺少该文件时，才从纯文本重建，
     * 并按音频时长均分时间轴，避免所有字幕落在 00:00:00。
     */
    private List<SpeechRecognitionService.RecognizedItem> loadRecognizedItems(
            Path recognizedJsonPath, Path recognizedTextPath, Path audioPath) {
        try {
            if (Files.exists(recognizedJsonPath)) {
                List<SpeechRecognitionService.RecognizedItem> cached = objectMapper.readValue(
                    recognizedJsonPath.toFile(),
                    new TypeReference<List<SpeechRecognitionService.RecognizedItem>>() {});
                if (cached != null && !cached.isEmpty()) {
                    log.info("从 recognized.json 恢复识别结果: {} 条", cached.size());
                    return cached;
                }
            }

            log.warn("未找到带时间戳的识别结果，回退到纯文本重建: {}", recognizedTextPath);
            List<String> lines = Files.readAllLines(recognizedTextPath).stream()
                .filter(line -> !line.isBlank())
                .map(String::trim)
                .toList();
            if (lines.isEmpty()) {
                return new ArrayList<>();
            }

            double duration = 0;
            if (Files.exists(audioPath)) {
                try {
                    duration = audioExtractor.getDuration(audioPath);
                } catch (Exception e) {
                    log.warn("获取音频时长失败，时间轴将无法均分: {}", e.getMessage());
                }
            }

            List<SpeechRecognitionService.RecognizedItem> items = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                SpeechRecognitionService.RecognizedItem item = new SpeechRecognitionService.RecognizedItem();
                item.setText(lines.get(i));
                item.setStartTime(duration * i / lines.size());
                item.setEndTime(duration * (i + 1) / lines.size());
                items.add(item);
            }
            log.info("纯文本重建完成: {} 条，按 {}s 均分", items.size(), (long) duration);
            return items;
        } catch (Exception e) {
            throw new RuntimeException("加载识别结果失败: " + e.getMessage(), e);
        }
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

    /**
     * 删除任务及其文件。
     *
     * @throws IllegalStateException 任务正在处理中，应先等待完成
     */
    public boolean deleteTask(String taskId) {
        if (runningTasks.contains(taskId)) {
            throw new IllegalStateException("任务正在处理中，请等待完成后再删除");
        }

        TaskStatus removed = tasks.remove(taskId);
        if (removed == null) return false;

        progressCallbacks.remove(taskId);
        String key = buildTaskKey(removed.getRequest());
        if (key != null) {
            taskKeyIndex.remove(key, taskId);
        }

        try {
            Path taskDir = fileManager.getTaskDir(taskId);
            if (Files.exists(taskDir)) {
                // try-with-resources 确保文件句柄释放，否则 Windows 下目录可能删不掉
                try (Stream<Path> walk = Files.walk(taskDir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                            // 文件被占用时跳过，交给下次清理
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.warn("删除任务目录失败: {}", taskId, e);
        }

        return true;
    }

    /**
     * 定期清理过期任务：内存条目与磁盘文件一并移除，并清理残留目录。
     */
    @Scheduled(fixedDelay = 3600_000L, initialDelay = 600_000L)
    public void cleanupExpiredTasks() {
        int retentionHours = Math.max(1, appProperties.getTaskRetentionHours());
        long cutoff = System.currentTimeMillis() - retentionHours * 3600_000L;

        List<String> expired = tasks.values().stream()
            .filter(s -> s.getCreatedAt() > 0 && s.getCreatedAt() < cutoff)
            .filter(s -> !runningTasks.contains(s.getTaskId()))
            .map(TaskStatus::getTaskId)
            .toList();

        for (String id : expired) {
            try {
                if (deleteTask(id)) {
                    log.info("已清理过期任务: {}", id);
                }
            } catch (IllegalStateException e) {
                log.debug("任务在清理时开始运行，跳过: {}", id);
            }
        }

        // 清理不属于任何任务的残留目录（服务中断等情况留下的）
        fileManager.cleanupOrphanDirs(tasks.keySet(), retentionHours);
    }
}
