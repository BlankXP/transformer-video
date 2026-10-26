package com.bili.translator.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Range 头解析的边界行为。
 *
 * <p>这些分支直接决定播放器能否拖动进度条：末端越界必须截断而不是 416，
 * 后缀范围（Safari 会用）必须支持。
 */
class ByteRangeTest {

    @Test
    @DisplayName("闭区间按原样返回")
    void closedRange() {
        ByteRange range = ByteRange.parse("bytes=0-99", 1000);

        assertThat(range.satisfiable()).isTrue();
        assertThat(range.start()).isZero();
        assertThat(range.end()).isEqualTo(99);
        assertThat(range.length()).isEqualTo(100);
    }

    @Test
    @DisplayName("开区间取到文件末尾")
    void openEnded() {
        ByteRange range = ByteRange.parse("bytes=100-", 1000);

        assertThat(range.start()).isEqualTo(100);
        assertThat(range.end()).isEqualTo(999);
    }

    @Test
    @DisplayName("末端越界截断到文件末尾，而不是整段拒绝")
    void endBeyondFileIsClamped() {
        ByteRange range = ByteRange.parse("bytes=900-5000", 1000);

        assertThat(range.satisfiable()).isTrue();
        assertThat(range.start()).isEqualTo(900);
        assertThat(range.end()).isEqualTo(999);
    }

    @Test
    @DisplayName("后缀范围取最后 N 字节")
    void suffixRange() {
        ByteRange range = ByteRange.parse("bytes=-100", 1000);

        assertThat(range.start()).isEqualTo(900);
        assertThat(range.end()).isEqualTo(999);
    }

    @Test
    @DisplayName("后缀长度超过文件大小时从头开始")
    void suffixLargerThanFile() {
        ByteRange range = ByteRange.parse("bytes=-5000", 1000);

        assertThat(range.start()).isZero();
        assertThat(range.end()).isEqualTo(999);
    }

    @Test
    @DisplayName("起点越过文件末尾时不可满足")
    void startBeyondFileIsUnsatisfiable() {
        assertThat(ByteRange.parse("bytes=1000-", 1000)).isSameAs(ByteRange.UNSATISFIABLE);
    }

    @Test
    @DisplayName("起点大于终点时不可满足")
    void startGreaterThanEnd() {
        assertThat(ByteRange.parse("bytes=500-100", 1000)).isSameAs(ByteRange.UNSATISFIABLE);
    }

    @Test
    @DisplayName("空文件不可满足")
    void emptyFile() {
        assertThat(ByteRange.parse("bytes=0-1", 0)).isSameAs(ByteRange.UNSATISFIABLE);
    }

    @Test
    @DisplayName("多区间整头忽略，调用方回退 200 全量")
    void multipleRangesIgnored() {
        assertThat(ByteRange.parse("bytes=0-1,5-6", 1000)).isNull();
    }

    @Test
    @DisplayName("非 bytes 单位忽略")
    void otherUnitIgnored() {
        assertThat(ByteRange.parse("items=0-1", 1000)).isNull();
    }

    @Test
    @DisplayName("非法数字忽略")
    void invalidNumberIgnored() {
        assertThat(ByteRange.parse("bytes=a-b", 1000)).isNull();
    }

    @Test
    @DisplayName("没有 Range 头时返回 null")
    void noHeader() {
        assertThat(ByteRange.parse(null, 1000)).isNull();
    }

    @Test
    @DisplayName("单位大小写不敏感")
    void caseInsensitiveUnit() {
        ByteRange range = ByteRange.parse("BYTES=0-9", 1000);

        assertThat(range.start()).isZero();
        assertThat(range.end()).isEqualTo(9);
    }
}
