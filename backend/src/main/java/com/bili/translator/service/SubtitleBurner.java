package com.bili.translator.service;

import com.bili.translator.config.AppProperties;
import com.bili.translator.util.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Service
public class SubtitleBurner {

    private static final Logger log = LoggerFactory.getLogger(SubtitleBurner.class);

    private final AppProperties appProperties;

    public SubtitleBurner(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public void burn(Path videoPath, Path srtPath, Path outputPath) throws Exception {
        String srtPathEscaped = escapePath(srtPath.toString());

        String filter = "subtitles=" + srtPathEscaped
            + ":force_style='FontName=Noto Sans CJK SC,FontSize=24,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,Outline=2,MarginV=30'";

        log.info("开始烧录字幕: video={}, srt={}, output={}", videoPath, srtPath, outputPath);

        ProcessRunner.ProcessResult result = ProcessRunner.run(
            List.of("ffmpeg", "-y",
                "-i", videoPath.toString(),
                "-vf", filter,
                "-c:v", "libx264",
                "-preset", "fast",
                "-crf", "23",
                "-c:a", "copy",
                "-movflags", "+faststart",
                outputPath.toString()),
            burnTimeout());

        if (!result.isSuccess()) {
            log.error("ffmpeg 字幕烧录失败，退出码: {}, 输出尾部: {}", result.exitCode(), tail(result.output()));
            throw new RuntimeException("ffmpeg 字幕烧录失败，退出码: " + result.exitCode());
        }

        log.info("字幕烧录完成: {}", outputPath);
    }

    /** 重编码整条视频，预留 6 倍视频上限 + 10 分钟余量 */
    private Duration burnTimeout() {
        return Duration.ofSeconds((long) appProperties.getMaxVideoDuration() * 6 + 600);
    }

    private String tail(String output) {
        if (output == null) return "";
        String trimmed = output.strip();
        return trimmed.length() <= 800 ? trimmed : trimmed.substring(trimmed.length() - 800);
    }

    private String escapePath(String path) {
        String escaped = path.replace("\\", "/");
        escaped = escaped.replace(":", "\\:");
        escaped = escaped.replace("'", "\\'");
        escaped = escaped.replace("[", "\\[");
        escaped = escaped.replace("]", "\\]");
        return escaped;
    }
}
