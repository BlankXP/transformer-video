package com.bili.translator.controller;

import com.bili.translator.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录失败记录的清理边界。
 *
 * <p>没有清理时，每个失败过一次的来源都会永久留在 Map 里（公网扫描器会带来大量来源）。
 * 但清理不能误伤两类记录：**正在锁定中的**（否则锁定被提前解除）和
 * **刚刚失败的**（否则计数被清零，连续失败永远到不了阈值）。
 */
class AuthControllerAttemptsTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long HOUR = 60 * 60 * 1000L;

    @Test
    @DisplayName("清理长期无失败的来源，保留锁定中与刚失败的记录")
    void purgeKeepsLockedAndRecentEntries() {
        AuthController controller = new AuthController(new AppProperties());

        // 阈值是 256，先填够 300 条「两小时前失败过」的陈旧记录
        for (int i = 0; i < 300; i++) {
            controller.attempts.put("10.0.0." + i, new long[]{1, 0, NOW - 2 * HOUR});
        }
        controller.attempts.put("locked", new long[]{0, NOW + 60_000L, NOW});
        controller.attempts.put("recent", new long[]{2, 0, NOW - 60_000L});

        controller.purgeExpired(NOW);

        assertThat(controller.attempts)
                .as("锁定中的来源不能被清掉，否则锁定形同虚设")
                .containsKey("locked");
        assertThat(controller.attempts)
                .as("一分钟前刚失败过，计数必须保留")
                .containsKey("recent");
        assertThat(controller.attempts)
                .as("超过 TTL 无新失败的陈旧记录应被清理")
                .doesNotContainKey("10.0.0.0")
                .hasSize(2);
    }

    @Test
    @DisplayName("规模未达阈值时不扫描，避免每次登录都做一次全量遍历")
    void purgeSkippedBelowThreshold() {
        AuthController controller = new AuthController(new AppProperties());
        controller.attempts.put("stale", new long[]{1, 0, NOW - 48 * HOUR});

        controller.purgeExpired(NOW);

        assertThat(controller.attempts).containsKey("stale");
    }
}
