package com.bili.translator.service;

import com.bili.translator.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Service
public class SpeechRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(SpeechRecognitionService.class);

    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/audio/transcriptions";

    private final AppProperties appProperties;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private BiConsumer<Double, String> progressCallback;

    public SpeechRecognitionService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void setProgressCallback(BiConsumer<Double, String> progressCallback) {
        this.progressCallback = progressCallback;
    }

    public List<RecognitionItem> recognize(Path audioPath, String language) {
        double duration = getAudioDuration(audioPath);
        int segmentDuration = appProperties.getAudioSegmentDuration();
        if (duration <= segmentDuration) {
            return recognizeSingle(audioPath, language);
        } else {
            return recognizeLong(audioPath, language, duration);
        }
    }

    private List<RecognitionItem> recognizeSingle(Path audioPath, String language) {
        int maxAttempts = 3;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                if (attempt > 0) {
                    long sleepMs = (long) Math.pow(2, attempt) * 1000;
                    Thread.sleep(sleepMs);
                }
                RequestBody fileBody = RequestBody.create(
                        audioPath.toFile(),
                        MediaType.parse("audio/wav")
                );
                MultipartBody multipartBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", audioPath.getFileName().toString(), fileBody)
                        .addFormDataPart("model", appProperties.getAsrModel())
                        .addFormDataPart("language", language)
                        .addFormDataPart("response_format", "verbose_json")
                        .build();
                Request request = new Request.Builder()
                        .url(API_URL)
                        .addHeader("Authorization", "Bearer " + appProperties.getDashscopeApiKey())
                        .post(multipartBody)
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        log.warn("ASR API returned status {}: {}", response.code(), responseBody);
                        if (attempt == maxAttempts - 1) {
                            throw new RuntimeException("ASR API failed after " + maxAttempts + " attempts: " + response.code());
                        }
                        continue;
                    }
                    return parseResponse(responseBody);
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                log.warn("ASR attempt {} failed: {}", attempt + 1, e.getMessage());
                if (attempt == maxAttempts - 1) {
                    throw new RuntimeException("ASR failed after " + maxAttempts + " attempts", e);
                }
            }
        }
        throw new RuntimeException("ASR failed after " + maxAttempts + " attempts");
    }

    private List<RecognitionItem> recognizeLong(Path audioPath, String language, double duration) {
        int segmentDuration = appProperties.getAudioSegmentDuration();
        int segmentCount = (int) Math.ceil(duration / segmentDuration);
        List<RecognitionItem> allItems = new ArrayList<>();
        for (int i = 0; i < segmentCount; i++) {
            double start = i * segmentDuration;
            String segmentFileName = audioPath.getFileName().toString().replace(".wav", "_seg" + i + ".wav");
            Path segmentPath = Path.of(appProperties.getTempDir(), segmentFileName);
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "ffmpeg", "-y", "-i", audioPath.toString(),
                        "-ss", String.valueOf(start),
                        "-t", String.valueOf(segmentDuration),
                        "-ar", "16000", "-ac", "1",
                        segmentPath.toString()
                );
                pb.redirectErrorStream(true);
                Process process = pb.start();
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("ffmpeg segment failed with code " + exitCode + ": " + output);
                }
                List<RecognitionItem> segmentItems = recognizeSingle(segmentPath, language);
                for (RecognitionItem item : segmentItems) {
                    allItems.add(new RecognitionItem(item.text(), item.startTime() + start, item.endTime() + start));
                }
                if (progressCallback != null) {
                    double progress = (double) (i + 1) / segmentCount;
                    progressCallback.accept(progress, "Recognizing segment " + (i + 1) + "/" + segmentCount);
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to recognize segment " + i, e);
            } finally {
                try {
                    Files.deleteIfExists(segmentPath);
                } catch (Exception e) {
                    log.warn("Failed to delete segment file: {}", segmentPath, e);
                }
            }
        }
        return allItems;
    }

    private double getAudioDuration(Path audioPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "quiet", "-print_format", "json",
                    "-show_format", audioPath.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("ffprobe exited with code " + exitCode);
            }
            JsonNode root = objectMapper.readTree(output.toString());
            JsonNode durationNode = root.path("format").path("duration");
            if (durationNode.isMissingNode()) {
                throw new RuntimeException("Duration not found in ffprobe output");
            }
            return durationNode.asDouble();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get audio duration", e);
        }
    }

    private List<RecognitionItem> parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<RecognitionItem> items = new ArrayList<>();
            JsonNode segments = root.path("segments");
            if (segments.isArray()) {
                for (JsonNode segment : segments) {
                    String text = segment.path("text").asText("");
                    double startTime = segment.path("start").asDouble(0.0);
                    double endTime = segment.path("end").asDouble(0.0);
                    items.add(new RecognitionItem(text, startTime, endTime));
                }
            }
            if (items.isEmpty()) {
                String fullText = root.path("text").asText("");
                if (!fullText.isEmpty()) {
                    items.add(new RecognitionItem(fullText, 0.0, 0.0));
                }
            }
            return items;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse ASR response: " + responseBody, e);
        }
    }

    public record RecognitionItem(String text, double startTime, double endTime) {}
}
