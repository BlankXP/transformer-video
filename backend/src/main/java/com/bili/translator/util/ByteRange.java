package com.bili.translator.util;

/**
 * HTTP {@code Range} 头解析（单区间 {@code bytes} 形式）。
 *
 * <p>抽成独立类型的目的有二：一是让越界截断、后缀范围这类边界行为只有一处实现，
 * 二是它是纯函数，便于直接单测（此前解析逻辑内联在 controller 里无法覆盖）。
 *
 * <p>行为约定：
 * <ul>
 *   <li>{@link #parse} 返回 {@code null} → 无法识别的头，调用方回退为 200 全量响应；</li>
 *   <li>返回 {@link #UNSATISFIABLE} → 语法可识别但区间不可满足，调用方应返回 416；</li>
 *   <li>否则返回可满足的闭区间，末端已按 RFC 7233 截断到文件末尾。</li>
 * </ul>
 */
public record ByteRange(long start, long end) {

    /** 语法合法但无法满足的区间（起始位置越过文件末尾等） */
    public static final ByteRange UNSATISFIABLE = new ByteRange(-1, -1);

    public boolean satisfiable() {
        return start >= 0 && end >= start;
    }

    /** 区间字节数，用于 Content-Length */
    public long length() {
        return end - start + 1;
    }

    public static ByteRange parse(String header, long fileSize) {
        if (header == null) {
            return null;
        }
        String value = header.trim();
        if (!value.regionMatches(true, 0, "bytes=", 0, 6)) {
            return null;
        }

        String spec = value.substring(6).trim();
        // 多区间（bytes=0-1,5-6）浏览器播放器实际不用，按规范可忽略整头
        if (spec.isEmpty() || spec.indexOf(',') >= 0) {
            return null;
        }

        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }

        String rawStart = spec.substring(0, dash).trim();
        String rawEnd = spec.substring(dash + 1).trim();

        if (fileSize <= 0) {
            return UNSATISFIABLE;
        }

        try {
            if (rawStart.isEmpty()) {
                // bytes=-N：取最后 N 字节（Safari 等播放器会用）
                if (rawEnd.isEmpty()) {
                    return null;
                }
                long suffix = Long.parseLong(rawEnd);
                if (suffix <= 0) {
                    return UNSATISFIABLE;
                }
                return new ByteRange(Math.max(0, fileSize - suffix), fileSize - 1);
            }

            long start = Long.parseLong(rawStart);
            long end = rawEnd.isEmpty() ? fileSize - 1 : Long.parseLong(rawEnd);

            if (start < 0 || start > end || start >= fileSize) {
                return UNSATISFIABLE;
            }
            // 末端越界时截断，而不是整段 416——否则部分播放器会直接报错
            return new ByteRange(start, Math.min(end, fileSize - 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
