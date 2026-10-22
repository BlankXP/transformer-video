package com.bili.translator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 子进程执行器：统一处理超时、输出采集与强制终止。
 *
 * <p>此前各服务分别使用裸 {@code process.waitFor()}，一旦 ffmpeg / yt-dlp 卡住，
 * 调用线程会被永久占用，8 个处理线程耗尽后整个服务不再接受新任务。
 *
 * <p>stdout 与 stderr 已合并，按行回调。超时后会先 {@code destroy()}，
 * 5 秒内未退出再 {@code destroyForcibly()}，并抛出 {@link ProcessTimeoutException}。
 */
public final class ProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class);

    /** 采集输出的上限，避免长任务把内存吃满 */
    private static final int MAX_CAPTURE_CHARS = 256 * 1024;

    private ProcessRunner() {
    }

    public static ProcessResult run(List<String> command, Duration timeout) throws Exception {
        return run(command, timeout, null);
    }

    public static ProcessResult run(List<String> command, Duration timeout, Consumer<String> onLine) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        long deadline = System.nanoTime() + timeout.toNanos();

        StringBuilder captured = new StringBuilder();
        AtomicBoolean killed = new AtomicBoolean(false);

        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "proc-watchdog");
            t.setDaemon(true);
            return t;
        });
        ScheduledFuture<?> killer = watchdog.schedule(() -> {
            killed.set(true);
            log.warn("子进程超时（{}s），强制终止: {}", timeout.toSeconds(), command.get(0));
            killTree(process);
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        try {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (captured.length() < MAX_CAPTURE_CHARS) {
                        captured.append(line).append('\n');
                    }
                    if (onLine != null) {
                        try {
                            onLine.accept(line);
                        } catch (Exception e) {
                            log.debug("处理子进程输出行失败: {}", e.getMessage());
                        }
                    }
                }
            }

            long remainMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            boolean finished = remainMs > 0 && process.waitFor(remainMs, TimeUnit.MILLISECONDS);

            if (!finished || killed.get()) {
                killTree(process);
                throw new ProcessTimeoutException(
                        "子进程超时（" + timeout.toSeconds() + "s）: " + command.get(0));
            }

            return new ProcessResult(process.exitValue(), captured.toString());
        } finally {
            killer.cancel(false);
            watchdog.shutdownNow();
            if (process.isAlive()) {
                killTree(process);
            }
        }
    }

    /**
     * 强制终止整棵进程树。
     *
     * <p>只 destroy 父进程不足以释放输出管道——例如 yt-dlp 会派生 ffmpeg 合并音视频，
     * 父进程被杀死后子进程仍持有 stdout，读取线程会一直阻塞到子进程自然结束。
     */
    private static void killTree(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (Exception e) {
            log.debug("终止子进程失败: {}", e.getMessage());
        }
        process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record ProcessResult(int exitCode, String output) {

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }
}
