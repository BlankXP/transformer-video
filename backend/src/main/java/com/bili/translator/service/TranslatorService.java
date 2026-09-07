package com.bili.translator.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TranslatorService {

    private static final Logger log = LoggerFactory.getLogger(TranslatorService.class);

    private final AppProperties appProperties;
    private static final int BATCH_SIZE = 50;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TranslatorService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 批量翻译字幕，将字幕按批次发送给大模型进行翻译
     */
    public List<TranslatedItem> translate(List<SpeechRecognitionService.RecognizedItem> subtitles,
                                           String targetLanguage,
                                           BiConsumer<Float, String> progressCallback) throws Exception {
        List<Integer> nonEmptyIndices = new ArrayList<>();
        List<SpeechRecognitionService.RecognizedItem> nonEmptyItems = new ArrayList<>();
        for (int i = 0; i < subtitles.size(); i++) {
            String text = subtitles.get(i).getText();
            if (text != null && !text.isBlank()) {
                nonEmptyIndices.add(i);
                nonEmptyItems.add(subtitles.get(i));
            }
        }

        int skipped = subtitles.size() - nonEmptyItems.size();
        if (skipped > 0) {
            log.info("跳过{}条空内容字幕，实际翻译{}条", skipped, nonEmptyItems.size());
        }

        List<TranslatedItem> results = new ArrayList<>(subtitles.size());
        for (int i = 0; i < subtitles.size(); i++) {
            results.add(null);
        }

        if (nonEmptyItems.isEmpty()) {
            log.info("所有字幕内容为空，无需翻译");
            for (int i = 0; i < subtitles.size(); i++) {
                results.set(i, new TranslatedItem("", "", subtitles.get(i).getStartTime(), subtitles.get(i).getEndTime()));
            }
            return results;
        }

        int totalBatches = (nonEmptyItems.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        log.info("开始翻译字幕: 共{}条(非空), 分{}批, 自动检测→{}", nonEmptyItems.size(), totalBatches, targetLanguage);

        for (int i = 0; i < nonEmptyItems.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, nonEmptyItems.size());
            List<SpeechRecognitionService.RecognizedItem> batch = nonEmptyItems.subList(i, end);
            int batchNum = i / BATCH_SIZE + 1;

            List<String> translatedTexts;
            try {
                translatedTexts = translateBatch(batch, targetLanguage);
            } catch (Exception e) {
                log.warn("第{}批翻译失败，尝试拆分为更小批次重试: {}", batchNum, e.getMessage());
                translatedTexts = translateWithSubBatches(batch, targetLanguage, batchNum);
            }

            for (int j = 0; j < batch.size(); j++) {
                SpeechRecognitionService.RecognizedItem sub = batch.get(j);
                String translated = j < translatedTexts.size() ? translatedTexts.get(j) : "";
                int originalIndex = nonEmptyIndices.get(i + j);
                results.set(originalIndex, new TranslatedItem(sub.getText(), translated, sub.getStartTime(), sub.getEndTime()));
            }

            log.info("第{}/{}批翻译完成, 本批{}条", batchNum, totalBatches, batch.size());

            if (progressCallback != null) {
                progressCallback.accept((float) batchNum / totalBatches, "翻译进度 " + batchNum + "/" + totalBatches);
            }
        }

        for (int i = 0; i < subtitles.size(); i++) {
            if (results.get(i) == null) {
                SpeechRecognitionService.RecognizedItem sub = subtitles.get(i);
                results.set(i, new TranslatedItem(sub.getText(), "", sub.getStartTime(), sub.getEndTime()));
            }
        }

        log.info("字幕翻译全部完成, 共{}条(其中{}条为空内容跳过翻译)", results.size(), skipped);
        return results;
    }

    /**
     * 翻译一批字幕，将字幕编号后拼接为提示词发送给模型
     */
    private List<String> translateBatch(List<SpeechRecognitionService.RecognizedItem> batch,
                                         String targetLanguage) throws Exception {
        StringBuilder numberedText = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            numberedText.append("[").append(i + 1).append("] ").append(batch.get(i).getText()).append("\n");
        }

        String prompt = String.format(
            "请将以下字幕文本翻译为%s，自动识别源语言（可能包含多种语言），保持原文的语义和语气。每行一个字幕，保持编号格式（[编号] 翻译内容），严格一一对应，不要添加额外解释：\n\n%s",
            targetLanguage, numberedText.toString()
        );

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return callQwenApi(prompt, batch.size());
            } catch (Exception e) {
                log.warn("翻译第{}次尝试失败: {}", attempt + 1, e.getMessage());
                if (attempt < 2) {
                    long delay = (long) Math.pow(2, attempt) * 1000;
                    log.info("等待{}ms后重试...", delay);
                    Thread.sleep(delay);
                } else {
                    log.error("翻译3次尝试均失败", e);
                    throw e;
                }
            }
        }
        throw new RuntimeException("翻译失败");
    }

    private List<String> translateWithSubBatches(List<SpeechRecognitionService.RecognizedItem> batch,
                                                   String targetLanguage, int batchNum) {
        List<String> allTranslated = new ArrayList<>();
        int subSize = Math.max(1, batch.size() / 2);

        if (subSize >= batch.size()) {
            log.warn("第{}批已无法继续拆分(每批仅{}条)，尝试OpenRouter回退翻译", batchNum, batch.size());
            for (SpeechRecognitionService.RecognizedItem item : batch) {
                try {
                    String translated = translateSingleViaOpenRouter(item.getText(), targetLanguage);
                    allTranslated.add(translated);
                } catch (Exception e) {
                    log.warn("OpenRouter回退翻译也失败，该条翻译结果置空: {}", e.getMessage());
                    allTranslated.add("");
                }
            }
            return allTranslated;
        }

        log.info("第{}批拆分为子批(每批{}条)重试", batchNum, subSize);

        for (int i = 0; i < batch.size(); i += subSize) {
            int end = Math.min(i + subSize, batch.size());
            List<SpeechRecognitionService.RecognizedItem> subBatch = batch.subList(i, end);
            try {
                List<String> subResult = translateBatch(subBatch, targetLanguage);
                allTranslated.addAll(subResult);
                log.info("第{}批子批{}/{}重试成功, {}条", batchNum, (i / subSize) + 1, (batch.size() + subSize - 1) / subSize, subBatch.size());
            } catch (Exception e) {
                log.warn("第{}批子批翻译失败，递归拆分重试: {}", batchNum, e.getMessage());
                List<String> deeperResult = translateWithSubBatches(subBatch, targetLanguage, batchNum * 100 + i);
                allTranslated.addAll(deeperResult);
            }
        }

        return allTranslated;
    }

    /**
     * 通过OpenRouter API翻译单条文本（作为DashScope翻译失败的回退方案）
     */
    private String translateSingleViaOpenRouter(String text, String targetLanguage) throws Exception {
        String apiKey = appProperties.getOpenrouterApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("OpenRouter API Key未配置");
        }

        String prompt = String.format(
            "请将以下文本翻译为%s（自动识别源语言），只输出翻译结果，不要添加任何解释：\n\n%s",
            targetLanguage, text
        );

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", appProperties.getOpenrouterModel());

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);

        ObjectNode reasoning = objectMapper.createObjectNode();
        reasoning.put("enabled", true);
        requestBody.set("reasoning", reasoning);

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        log.debug("OpenRouter请求体: {}", jsonBody);

        Request request = new Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("OpenRouter API返回错误: HTTP " + response.code() + " - " + responseBody);
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isEmpty()) {
                throw new RuntimeException("OpenRouter API返回空choices");
            }

            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new RuntimeException("OpenRouter API返回空内容");
            }

            log.info("OpenRouter回退翻译成功: 原文长度={}, 译文长度={}", text.length(), content.length());
            return content.trim();
        }
    }

    /**
     * 调用DashScope SDK进行文本生成翻译
     */
    private List<String> callQwenApi(String prompt, int expectedCount) throws Exception {
        Generation gen = new Generation();
        GenerationParam param = GenerationParam.builder()
            .apiKey(appProperties.getDashscopeApiKey())
            .model(appProperties.getTranslationModel())
            .messages(Arrays.asList(
                Message.builder()
                    .role(Role.USER.getValue())
                    .content(prompt)
                    .build()
            ))
            .resultFormat(GenerationParam.ResultFormat.MESSAGE)
            .build();

        log.debug("调用翻译API: model={}, 期望{}条结果", appProperties.getTranslationModel(), expectedCount);
        GenerationResult result = gen.call(param);
        String text = result.getOutput().getChoices().get(0).getMessage().getContent();
        log.debug("翻译API原始返回: {}", text);
        return parseTranslated(text, expectedCount);
    }

    /**
     * 解析模型返回的编号格式翻译结果
     */
    private static final Pattern NUMBERED_LINE = Pattern.compile("^\\[\\d+\\]\\s+(.*)$");

    private List<String> parseTranslated(String text, int expectedCount) {
        List<String> results = new ArrayList<>();
        String[] lines = text.trim().split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Matcher matcher = NUMBERED_LINE.matcher(line);
            if (matcher.matches()) {
                results.add(matcher.group(1).trim());
            } else {
                results.add(line);
            }
        }

        if (results.size() < expectedCount) {
            log.warn("翻译结果数量不足: 期望{}条, 实际{}条, 缺失部分将填充空字符串", expectedCount, results.size());
        } else if (results.size() > expectedCount) {
            log.warn("翻译结果数量超出: 期望{}条, 实际{}条, 将截断", expectedCount, results.size());
        }

        while (results.size() < expectedCount) {
            results.add("");
        }
        return results.subList(0, Math.min(results.size(), expectedCount));
    }

    public static class TranslatedItem {
        private final String text;
        private final String translatedText;
        private final double startTime;
        private final double endTime;

        public TranslatedItem(String text, String translatedText, double startTime, double endTime) {
            this.text = text;
            this.translatedText = translatedText;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public String getText() { return text; }
        public String getTranslatedText() { return translatedText; }
        public double getStartTime() { return startTime; }
        public double getEndTime() { return endTime; }
    }
}
