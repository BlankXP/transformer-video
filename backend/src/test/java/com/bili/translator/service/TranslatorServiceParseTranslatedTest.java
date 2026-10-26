package com.bili.translator.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 翻译结果解析。
 *
 * <p>模型输出不可靠：可能乱序、缺号、重复编号、把一条译文折成多行。这里的行为是
 * 「宁可抛异常触发重试，也不静默产出错位字幕」——错位字幕烧进视频是最难发现的故障。
 */
class TranslatorServiceParseTranslatedTest {

    @Test
    @DisplayName("正常编号按顺序解析")
    void normal() {
        assertThat(TranslatorService.parseTranslated("[1] 你好\n[2] 世界", 2))
                .containsExactly("你好", "世界");
    }

    @Test
    @DisplayName("乱序返回时按编号归位，而不是按行顺序")
    void outOfOrder() {
        assertThat(TranslatorService.parseTranslated("[2] 世界\n[1] 你好", 2))
                .containsExactly("你好", "世界");
    }

    @Test
    @DisplayName("缺号抛异常，避免静默产出错位字幕")
    void missingIndex() {
        assertThatThrownBy(() -> TranslatorService.parseTranslated("[1] 你好", 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺失");
    }

    @Test
    @DisplayName("编号存在但内容为空同样算缺失")
    void blankContent() {
        assertThatThrownBy(() -> TranslatorService.parseTranslated("[1] 你好\n[2]", 2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("重复编号保留首次出现的内容")
    void duplicatedIndex() {
        assertThat(TranslatorService.parseTranslated("[1] a\n[1] b\n[2] c", 2))
                .containsExactly("a", "c");
    }

    @Test
    @DisplayName("模型把一条译文折成多行时续接到上一条")
    void multiLineContinuation() {
        assertThat(TranslatorService.parseTranslated("[1] 你好\n世界\n[2] x", 2))
                .containsExactly("你好 世界", "x");
    }

    @Test
    @DisplayName("多余编号只取前 expectedCount 条")
    void extraIndexes() {
        assertThat(TranslatorService.parseTranslated("[1] a\n[2] b\n[3] c", 2))
                .containsExactly("a", "b");
    }

    @Test
    @DisplayName("首个编号之前的噪声行被忽略")
    void leadingNoise() {
        assertThat(TranslatorService.parseTranslated("以下是翻译结果：\n[1] a", 1))
                .containsExactly("a");
    }
}
