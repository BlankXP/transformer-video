package com.bili.translator.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.bili.translator.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

@Service
public class TranslatorService {

    private static final Logger log = LoggerFactory.getLogger(TranslatorService.class);

    private final AppProperties appProperties;
    private static final int BATCH_SIZE = 50;

    public TranslatorService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * 批量翻译字幕，将字幕按批次发送给大模型进行翻译
     */
    public List<TranslatedItem> translate(List<SpeechRecognitionService.RecognizedItem> subtitles,
                                           String sourceLanguage, String targetLanguage,
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
        log.info("开始翻译字幕: 共{}条(非空), 分{}批, {}→{}", nonEmptyItems.size(), totalBatches, sourceLanguage, targetLanguage);

        for (int i = 0; i < nonEmptyItems.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, nonEmptyItems.size());
            List<SpeechRecognitionService.RecognizedItem> batch = nonEmptyItems.subList(i, end);
            int batchNum = i / BATCH_SIZE + 1;

            List<String> translatedTexts = translateBatch(batch, sourceLanguage, targetLanguage);

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
                                         String sourceLanguage, String targetLanguage) throws Exception {
        StringBuilder numberedText = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            numberedText.append(i + 1).append(". ").append(batch.get(i).getText()).append("\n");
        }

        String prompt = String.format(
            "请将以下字幕文本从%s翻译为%s，保持原文的语义和语气。每行一个字幕，保持编号格式（编号. 翻译内容），不要添加额外解释：\n\n%s",
            sourceLanguage, targetLanguage, numberedText.toString()
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
    private List<String> parseTranslated(String text, int expectedCount) {
        List<String> results = new ArrayList<>();
        String[] lines = text.trim().split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int dotIndex = line.indexOf(". ");
            if (dotIndex > 0) {
                results.add(line.substring(dotIndex + 2));
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
