package com.bili.translator.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 视频地址白名单校验。
 *
 * <p>这是唯一阻止用户把任意地址丢给 yt-dlp 的关口（SSRF / 内网探测），
 * 后缀匹配写错等于白名单失效，所以单独锁住。
 */
class VideoUrlValidatorTest {

    @Test
    @DisplayName("BV 号补全为完整地址")
    void bvIdShortForm() {
        assertThat(VideoUrlValidator.validateAndNormalize("BV1xx411c7mD"))
                .isEqualTo("https://www.bilibili.com/video/BV1xx411c7mD");
    }

    @Test
    @DisplayName("av 号补全为完整地址")
    void avIdShortForm() {
        assertThat(VideoUrlValidator.validateAndNormalize("av170001"))
                .isEqualTo("https://www.bilibili.com/video/av170001");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.bilibili.com/video/BV1xx411c7mD",
            "https://b23.tv/abc123",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://sub.bilibili.com/video/av1"
    })
    @DisplayName("白名单站点的 https 地址原样返回")
    void allowedUrls(String url) {
        assertThat(VideoUrlValidator.validateAndNormalize(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://www.bilibili.com/video/BV1xx411c7mD",
            "https://evil.com/video",
            "https://bilibili.com.evil.com/video",
            "ftp://www.bilibili.com/video/BV1xx411c7mD",
            "https://notbilibili.com/x"
    })
    @DisplayName("非 https 或非白名单站点被拒绝")
    void rejectedUrls(String url) {
        assertThatThrownBy(() -> VideoUrlValidator.validateAndNormalize(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("空地址被拒绝")
    void blankRejected() {
        assertThatThrownBy(() -> VideoUrlValidator.validateAndNormalize("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }
}
