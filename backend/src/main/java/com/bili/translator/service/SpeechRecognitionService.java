package com.bili.translator.service;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.bili.translator.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
<<<<<<< HEAD
=======
import java.util.regex.Matcher;
import java.util.regex.Pattern;
>>>>>>> trae/solo-agent-DQFIa2

@Service
public class SpeechRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(SpeechRecognitionService.class);

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public SpeechRecognitionService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.objectMapper = new ObjectMapper();
    }

<<<<<<< HEAD
    /**
     * 识别音频文件，根据时长自动选择单段识别或分段识别
     */
    public List<RecognizedItem> recognize(Path audioPath, String language, BiConsumer<Float, String> progressCallback) throws Exception {
        double duration = getAudioDuration(audioPath);
        int segmentDuration = appProperties.getAudioSegmentDuration();
        log.info("开始语音识别: audioPath={}, language={}, duration={}s, segmentDuration={}s", audioPath, language, duration, segmentDuration);

        if (duration <= segmentDuration) {
            log.info("音频时长未超过分段阈值，使用单段识别");
            return recognizeSingle(audioPath, language);
        } else {
            log.info("音频时长超过分段阈值，使用分段识别");
            return recognizeLong(audioPath, language, duration, progressCallback);
        }
    }

    /**
     * 单段音频识别，带重试机制
     */
    private List<RecognizedItem> recognizeSingle(Path audioPath, String language) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return callRecognitionApi(audioPath, language);
=======
    public List<RecognizedItem> recognize(Path audioPath, BiConsumer<Float, String> progressCallback) throws Exception {
        double duration = getAudioDuration(audioPath);
        int segmentDuration = appProperties.getAudioSegmentDuration();
        log.info("开始语音识别: audioPath={}, duration={}s, segmentDuration={}s", audioPath, duration, segmentDuration);

        if (duration <= segmentDuration) {
            log.info("音频时长未超过分段阈值，使用单段识别");
            if (isEntirelySilent(audioPath, duration)) {
                log.info("音频完全静音，跳过语音识别");
                return Collections.emptyList();
            }
            return recognizeSingle(audioPath);
        } else {
            log.info("音频时长超过分段阈值，使用分段识别");
            return recognizeLong(audioPath, duration, progressCallback);
        }
    }

    private List<RecognizedItem> recognizeSingle(Path audioPath) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return callRecognitionApi(audioPath);
>>>>>>> trae/solo-agent-DQFIa2
            } catch (Exception e) {
                log.warn("语音识别第{}次尝试失败: {}", attempt + 1, e.getMessage());
                if (attempt < 2) {
                    long delay = (long) Math.pow(2, attempt) * 1000;
                    log.info("等待{}ms后重试...", delay);
                    Thread.sleep(delay);
                } else {
                    log.error("语音识别3次尝试均失败", e);
                    throw e;
                }
            }
        }
        throw new RuntimeException("语音识别失败");
    }

<<<<<<< HEAD
    /**
     * 长音频分段识别，将音频按指定时长切割后逐段识别
     */
    private List<RecognizedItem> recognizeLong(Path audioPath, String language, double duration, BiConsumer<Float, String> progressCallback) throws Exception {
        List<RecognizedItem> allResults = new ArrayList<>();
        int segmentDuration = appProperties.getAudioSegmentDuration();
        int segments = (int) (duration / segmentDuration) + 1;
        log.info("长音频分段识别: 共{}段", segments);

        for (int i = 0; i < segments; i++) {
            double start = i * segmentDuration;
            Path segmentPath = audioPath.getParent().resolve("segment_" + i + ".wav");
            log.debug("切割第{}段: start={}s, outputPath={}", i + 1, start, segmentPath);

            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", audioPath.toString(),
                "-ss", String.valueOf(start),
                "-t", String.valueOf(segmentDuration),
=======
    private List<RecognizedItem> recognizeLong(Path audioPath, double duration, BiConsumer<Float, String> progressCallback) throws Exception {
        List<AudioSegment> activeSegments = detectActiveSegments(audioPath, duration);
        log.info("检测到{}个有声音片段", activeSegments.size());

        if (activeSegments.isEmpty()) {
            log.info("未检测到有声音片段，跳过语音识别");
            return Collections.emptyList();
        }

        List<RecognizedItem> allResults = new ArrayList<>();
        int totalSegments = activeSegments.size();

        for (int i = 0; i < totalSegments; i++) {
            AudioSegment seg = activeSegments.get(i);
            Path segmentPath = audioPath.getParent().resolve("segment_" + i + ".wav");
            log.debug("切割第{}个有声音片段: start={}s, end={}s, outputPath={}", i + 1, seg.start, seg.end, segmentPath);

            double segDuration = seg.end - seg.start;
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", audioPath.toString(),
                "-ss", String.valueOf(seg.start),
                "-t", String.valueOf(segDuration),
>>>>>>> trae/solo-agent-DQFIa2
                "-ar", "16000", "-ac", "1",
                segmentPath.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {}
            }
            process.waitFor();

            if (!Files.exists(segmentPath)) {
<<<<<<< HEAD
                log.warn("第{}段音频切割后文件不存在，跳过", i + 1);
=======
                log.warn("第{}个有声音片段切割后文件不存在，跳过", i + 1);
>>>>>>> trae/solo-agent-DQFIa2
                continue;
            }

            List<RecognizedItem> results;
            try {
<<<<<<< HEAD
                results = recognizeSingle(segmentPath, language);
            } catch (Exception e) {
                log.warn("第{}段语音识别失败，跳过该段: {}", i + 1, e.getMessage());
                Files.deleteIfExists(segmentPath);
                if (progressCallback != null) {
                    progressCallback.accept((float)(i + 1) / segments, "第" + (i + 1) + "段识别失败已跳过 " + (i + 1) + "/" + segments);
=======
                results = recognizeSingle(segmentPath);
            } catch (Exception e) {
                log.warn("第{}个有声音片段语音识别失败，跳过: {}", i + 1, e.getMessage());
                Files.deleteIfExists(segmentPath);
                if (progressCallback != null) {
                    progressCallback.accept((float)(i + 1) / totalSegments, "第" + (i + 1) + "段识别失败已跳过 " + (i + 1) + "/" + totalSegments);
>>>>>>> trae/solo-agent-DQFIa2
                }
                continue;
            }
            for (RecognizedItem item : results) {
<<<<<<< HEAD
                item.setStartTime(item.getStartTime() + start);
                item.setEndTime(item.getEndTime() + start);
            }
            allResults.addAll(results);
            Files.deleteIfExists(segmentPath);
            log.info("第{}/{}段识别完成, 识别到{}条句子", i + 1, segments, results.size());

            if (progressCallback != null) {
                progressCallback.accept((float)(i + 1) / segments, "识别进度 " + (i + 1) + "/" + segments);
            }
        }

        log.info("长音频分段识别全部完成, 共识别到{}条句子", allResults.size());
        return allResults;
    }

    /**
     * 调用DashScope SDK流式回调接口进行语音识别，通过回调获取带真实时间戳的句子级结果
     */
    private List<RecognizedItem> callRecognitionApi(Path audioPath, String language) throws Exception {
=======
                item.setStartTime(item.getStartTime() + seg.start);
                item.setEndTime(item.getEndTime() + seg.start);
            }
            allResults.addAll(results);
            Files.deleteIfExists(segmentPath);
            log.info("第{}/{}个有声音片段识别完成, 识别到{}条句子", i + 1, totalSegments, results.size());

            if (progressCallback != null) {
                progressCallback.accept((float)(i + 1) / totalSegments, "识别进度 " + (i + 1) + "/" + totalSegments);
            }
        }

        log.info("有声音片段识别全部完成, 共识别到{}条句子", allResults.size());
        return allResults;
    }

    private List<AudioSegment> detectActiveSegments(Path audioPath, double totalDuration) {
        List<double[]> silenceRanges = detectSilence(audioPath, totalDuration);
        if (silenceRanges.isEmpty()) {
            List<AudioSegment> segments = new ArrayList<>();
            segments.add(new AudioSegment(0, totalDuration));
            return segments;
        }

        List<AudioSegment> activeSegments = new ArrayList<>();
        double prevEnd = 0;

        for (double[] silence : silenceRanges) {
            double silenceStart = silence[0];
            double silenceEnd = silence[1];

            if (silenceStart > prevEnd + 0.5) {
                activeSegments.add(new AudioSegment(prevEnd, silenceStart));
            }
            prevEnd = silenceEnd;
        }

        if (prevEnd < totalDuration - 0.5) {
            activeSegments.add(new AudioSegment(prevEnd, totalDuration));
        }

        for (AudioSegment seg : activeSegments) {
            log.info("有声音片段: start={}s, end={}s, duration={}s",
                String.format("%.2f", seg.start),
                String.format("%.2f", seg.end),
                String.format("%.2f", seg.end - seg.start));
        }

        return activeSegments;
    }

    private List<double[]> detectSilence(Path audioPath, double totalDuration) {
        List<double[]> silenceRanges = new ArrayList<>();
        int silenceDurationMs = 30000;
        double noiseDb = -50;

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-i", audioPath.toString(),
                "-af", "silencedetect=noise=" + noiseDb + "dB:d=" + (silenceDurationMs / 1000.0),
                "-f", "null", "-"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            List<Double> silenceStarts = new ArrayList<>();
            List<Double> silenceEnds = new ArrayList<>();

            Pattern startPattern = Pattern.compile("silence_start:\\s*(\\d+\\.?\\d*)");
            Pattern endPattern = Pattern.compile("silence_end:\\s*(\\d+\\.?\\d*)");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher startMatcher = startPattern.matcher(line);
                    if (startMatcher.find()) {
                        silenceStarts.add(Double.parseDouble(startMatcher.group(1)));
                    }
                    Matcher endMatcher = endPattern.matcher(line);
                    if (endMatcher.find()) {
                        silenceEnds.add(Double.parseDouble(endMatcher.group(1)));
                    }
                }
            }
            process.waitFor();

            for (int i = 0; i < silenceStarts.size(); i++) {
                double start = silenceStarts.get(i);
                double end = (i < silenceEnds.size()) ? silenceEnds.get(i) : totalDuration;
                silenceRanges.add(new double[]{start, end});
                log.debug("静音区间: start={}s, end={}s", String.format("%.2f", start), String.format("%.2f", end));
            }

            log.info("检测到{}个静音区间", silenceRanges.size());
        } catch (Exception e) {
            log.warn("静音检测失败，将按完整音频处理: {}", e.getMessage());
            return Collections.emptyList();
        }

        return silenceRanges;
    }

    private boolean isEntirelySilent(Path audioPath, double totalDuration) {
        List<double[]> silenceRanges = detectSilence(audioPath, totalDuration);
        if (silenceRanges.isEmpty()) {
            return false;
        }
        double totalSilence = 0;
        for (double[] range : silenceRanges) {
            totalSilence += range[1] - range[0];
        }
        return totalSilence >= totalDuration * 0.95;
    }

    private List<RecognizedItem> callRecognitionApi(Path audioPath) throws Exception {
>>>>>>> trae/solo-agent-DQFIa2
        Recognition recognizer = new Recognition();
        List<RecognizedItem> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        RecognitionParam param = RecognitionParam.builder()
            .apiKey(appProperties.getDashscopeApiKey())
            .model(appProperties.getAsrModel())
            .format("wav")
            .sampleRate(16000)
<<<<<<< HEAD
            .parameter("language_hints", new String[]{language})
            .build();

        log.debug("调用语音识别API(流式回调): model={}, language={}, file={}", appProperties.getAsrModel(), language, audioPath);
=======
            .build();

        log.debug("调用语音识别API(流式回调, 自动检测语言): model={}, file={}", appProperties.getAsrModel(), audioPath);
>>>>>>> trae/solo-agent-DQFIa2

        ResultCallback<RecognitionResult> callback = new ResultCallback<RecognitionResult>() {
            @Override
            public void onEvent(RecognitionResult result) {
                if (result.isSentenceEnd()) {
                    RecognizedItem item = new RecognizedItem();
                    item.setText(result.getSentence().getText());
                    item.setStartTime(result.getSentence().getBeginTime() / 1000.0);
                    item.setEndTime(result.getSentence().getEndTime() / 1000.0);
                    results.add(item);
                    log.debug("收到完整句子: text={}, start={}s, end={}s",
                        item.getText(), item.getStartTime(), item.getEndTime());
                }
            }

            @Override
            public void onComplete() {
                log.debug("语音识别回调完成");
                latch.countDown();
            }

            @Override
            public void onError(Exception e) {
                log.error("语音识别回调出错: {}", e.getMessage(), e);
                errorRef.set(e);
                latch.countDown();
            }
        };

        try {
            recognizer.call(param, callback);

            try (FileInputStream fis = new FileInputStream(audioPath.toFile())) {
                byte[] buffer = new byte[3200];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    ByteBuffer byteBuffer = bytesRead < buffer.length
                        ? ByteBuffer.wrap(buffer, 0, bytesRead)
                        : ByteBuffer.wrap(buffer);
                    recognizer.sendAudioFrame(byteBuffer);
                    buffer = new byte[3200];
                    Thread.sleep(100);
                }
            }

            recognizer.stop();
            latch.await(300, TimeUnit.SECONDS);

            if (errorRef.get() != null) {
                throw new RuntimeException("语音识别失败", errorRef.get());
            }

            log.info("语音识别完成, 共{}条句子", results.size());
            return results;
        } finally {
            recognizer.getDuplexApi().close(1000, "bye");
        }
    }

<<<<<<< HEAD
    /**
     * 使用ffprobe获取音频文件时长
     */
=======
>>>>>>> trae/solo-agent-DQFIa2
    private double getAudioDuration(Path audioPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", audioPath.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line);
        }
        process.waitFor();
        JsonNode root = objectMapper.readTree(output.toString());
        double duration = root.path("format").path("duration").asDouble();
        log.debug("获取音频时长: path={}, duration={}s", audioPath, duration);
        return duration;
    }

<<<<<<< HEAD
=======
    private static class AudioSegment {
        final double start;
        final double end;

        AudioSegment(double start, double end) {
            this.start = start;
            this.end = end;
        }
    }

>>>>>>> trae/solo-agent-DQFIa2
    public static class RecognizedItem {
        private String text;
        private double startTime;
        private double endTime;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public double getStartTime() {
            return startTime;
        }

        public void setStartTime(double startTime) {
            this.startTime = startTime;
        }

        public double getEndTime() {
            return endTime;
        }

        public void setEndTime(double endTime) {
            this.endTime = endTime;
        }
    }
}
