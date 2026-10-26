package com.bili.translator.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务键归一化。
 *
 * <p>查重全靠这个函数：归一化不一致会产生重复任务，归一化过度（把不同视频压成同一个键）
 * 会让第二次提交直接命中旧任务。两种错误都不会报错，只会悄悄出错，所以必须锁住。
 */
class PipelineProcessorNormalizeUrlTest {

    @Test
    @DisplayName("从完整地址中提取 BV 号并统一大写")
    void extractBv() {
        assertThat(PipelineProcessor.normalizeUrl("https://www.bilibili.com/video/BV1xx411c7mD?p=2"))
                .isEqualTo("BV1XX411C7MD");
    }

    @Test
    @DisplayName("小写 bv 也能识别并归一化")
    void lowercaseBv() {
        assertThat(PipelineProcessor.normalizeUrl("https://b23.tv/bv1xx411c7md"))
                .isEqualTo("BV1XX411C7MD");
    }

    @Test
    @DisplayName("av 号归一化为 AV + 数字")
    void extractAv() {
        assertThat(PipelineProcessor.normalizeUrl("https://www.bilibili.com/video/av170001"))
                .isEqualTo("AV170001");
    }

    @Test
    @DisplayName("普通地址去掉协议、www、查询串与尾部斜杠")
    void normalizeGenericUrl() {
        assertThat(PipelineProcessor.normalizeUrl("https://www.youtube.com/watch?v=abc"))
                .isEqualTo("youtube.com/watch");
        assertThat(PipelineProcessor.normalizeUrl("https://youtu.be/dQw4w9WgXcQ/"))
                .isEqualTo("youtu.be/dQw4w9WgXcQ");
    }

    @Test
    @DisplayName("空值归一化为空串")
    void blank() {
        assertThat(PipelineProcessor.normalizeUrl(null)).isEmpty();
        assertThat(PipelineProcessor.normalizeUrl("   ")).isEmpty();
    }
}
