package com.bili.translator.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 视频地址校验：限制协议与站点，避免把任意地址交给 yt-dlp 造成 SSRF 或内网探测。
 */
public final class VideoUrlValidator {

    /** 允许的站点及其子域 */
    private static final List<String> ALLOWED_HOSTS = List.of(
            "bilibili.com",
            "b23.tv",
            "youtube.com",
            "youtu.be");

    /** 直接以编号形式提交的情况，如 BV1xx411c7mD / av170001 */
    private static final Pattern SHORT_ID = Pattern.compile("(?i)^(BV[0-9A-Za-z]{10}|av\\d+)$");

    private VideoUrlValidator() {
    }

    /**
     * 校验并归一化视频地址。
     *
     * @return 可直接交给下载器的完整 URL
     * @throws IllegalArgumentException 地址为空、协议不支持或站点不在白名单
     */
    public static String validateAndNormalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("视频地址不能为空");
        }
        String url = raw.trim();

        if (SHORT_ID.matcher(url).matches()) {
            return "https://www.bilibili.com/video/" + url;
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("视频地址格式不正确");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("仅支持 https 协议的视频地址");
        }

        String host = uri.getHost();
        if (host == null || !isAllowedHost(host)) {
            throw new IllegalArgumentException("不支持的站点：" + host + "，仅支持 B 站与 YouTube");
        }

        return url;
    }

    private static boolean isAllowedHost(String host) {
        String lower = host.toLowerCase();
        for (String allowed : ALLOWED_HOSTS) {
            if (lower.equals(allowed) || lower.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }
}
