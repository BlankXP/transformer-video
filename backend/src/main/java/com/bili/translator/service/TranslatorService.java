package com.bili.translator.service;

import com.bili.translator.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

@Service
public class TranslatorService {

    private static final Logger log = LoggerFactory.getLogger(TranslatorService.class);

    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 3;

    private final AppProperties appProperties;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private BiConsumer<Double, String> progressCallback;

    public TranslatorService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public void setProgressCallback(BiConsumer<Double, String> progressCallback) {
        this.progressCallback = progressCallback;
    }

    public List<TranslatedItem> translate(List<RecognizedItem> subtitles, String sourceLanguage, String targetLanguage) {
        List<TranslatedItem> results = new ArrayList<>();
        int totalBatches = (subtitles.size() + BATCH_SIZE - 1) / BATCH_SIZE;

        for (int i = 0; i < subtitles.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, subtitles.size());
            List<RecognizedItem> batch = subtitles.subList(i, end);
            int batchNum = i / BATCH_SIZE + 1;

            List<String> translatedTexts = translateBatch(batch, sourceLanguage, targetLanguage);

            for (int j = 0; j < batch.size(); j++) {
                RecognizedItem item = batch.get(j);
                String translated = j < translatedTexts.size() ? translatedTexts.get(j) : "";
                results.add(new TranslatedItem(item.text, translated, item.startTime, item.endTime));
            }

            if (progressCallback != null) {
                progressCallback.accept((double) batchNum / totalBatches, "翻译进度 " + batchNum + "/" + totalBatches);
            }
        }

        return results;
    }

    private List<String> translateBatch(List<RecognizedItem> batch, String sourceLanguage, String targetLanguage) {
        StringBuilder numberedText = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            numberedText.append(i + 1).append(". ").append(batch.get(i).text);
            if (i < batch.size() - 1) {
                numberedText.append("\n");
            }
        }

        String prompt = "请将以下字幕文本从" + sourceLanguage + "翻译为" + targetLanguage +
                "，保持原文的语义和语气。每行一个字幕，保持编号格式（编号. 翻译内容），不要添加额外解释：\n\n" +
                numberedText;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                String responseBody = callApi(prompt);
                return parseTranslated(responseBody, batch.size());
            } catch (Exception e) {
                log.warn("Translation attempt {} failed: {}", attempt + 1, e.getMessage());
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep((long) Math.pow(2, attempt) * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Translation interrupted", ie);
                    }
                } else {
                    throw new RuntimeException("Translation failed after " + MAX_RETRIES + " attempts", e);
                }
            }
        }

        throw new RuntimeException("Translation failed after " + MAX_RETRIES + " attempts");
    }

    private String callApi(String prompt) throws IOException {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", appProperties.getTranslationModel());

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + appProperties.getDashscopeApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "no body";
                throw new IOException("API call failed with code " + response.code() + ": " + errorBody);
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    private List<String> parseTranslated(String responseContent, int expectedCount) {
        try {
            JsonNode root = objectMapper.readTree(responseContent);
            String text = root.path("choices").path(0).path("message").path("content").asText("");
            return parseTranslatedLines(text, expectedCount);
        } catch (Exception e) {
            log.error("Failed to parse translation response: {}", e.getMessage());
            List<String> results = new ArrayList<>();
            for (int i = 0; i < expectedCount; i++) {
                results.add("");
            }
            return results;
        }
    }

    private List<String> parseTranslatedLines(String text, int expectedCount) {
        String[] lines = text.strip().split("\n");
        List<String> results = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains(". ")) {
                String[] parts = trimmed.split("\\. ", 2);
                results.add(parts[1]);
            } else {
                results.add(trimmed);
            }
        }
        while (results.size() < expectedCount) {
            results.add("");
        }
        return results.subList(0, expectedCount);
    }

    public static class RecognizedItem {
        public String text;
        public double startTime;
        public double endTime;

        public RecognizedItem(String text, double startTime, double endTime) {
            this.text = text;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    public static class TranslatedItem {
        public String text;
        public String translatedText;
        public double startTime;
        public double endTime;

        public TranslatedItem(String text, String translatedText, double startTime, double endTime) {
            this.text = text;
            this.translatedText = translatedText;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}
