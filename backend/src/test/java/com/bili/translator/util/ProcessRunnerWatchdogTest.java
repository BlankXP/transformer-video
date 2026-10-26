package com.bili.translator.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 共享看门狗的队列行为。
 *
 * <p>锁的是一个真实回归：把「每个子进程一个调度器」改成共享单例后，
 * 若不开 {@code removeOnCancelPolicy}，{@code cancel()} 只标记状态、不移出延时队列，
 * 任务会一直留到延迟到期。这里的延迟按 {@code max-video-duration} 推算常达数小时
 * （60 分钟视频 ≈ 6.2 小时），期间持续持有 {@link Process} 与命令行引用，
 * 队列会随子进程数量不断增长。
 */
class ProcessRunnerWatchdogTest {

    @Test
    @DisplayName("取消的超时任务立即离开队列，而不是留到数小时后到期")
    void cancelledTaskIsRemovedImmediately() {
        ScheduledThreadPoolExecutor executor = ProcessRunner.createWatchdog();
        try {
            ScheduledFuture<?> future = executor.schedule(() -> { }, 3600, TimeUnit.SECONDS);
            assertThat(executor.getQueue())
                    .as("刚提交的延时任务应在队列中")
                    .hasSize(1);

            future.cancel(false);

            assertThat(executor.getQueue())
                    .as("取消后队列应立即清空；否则任务会持有 Process 引用直到延迟到期")
                    .isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("生产使用的共享实例同样开启了取消即移除策略")
    void sharedWatchdogHasPolicyEnabled() {
        assertThat(ProcessRunner.isRemoveOnCancelEnabled()).isTrue();
    }
}
