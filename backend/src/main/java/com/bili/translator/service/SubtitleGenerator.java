package com.bili.translator.service;

import com.bili.translator.model.SubtitleEntry;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class SubtitleGenerator {

    public void generateSrt(List<SubtitleEntry> subtitles, Path outputPath, boolean bilingual) throws Exception {
        String srtContent = formatSrt(subtitles, bilingual);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] contentBytes = srtContent.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + contentBytes.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(contentBytes, 0, withBom, bom.length, contentBytes.length);
        Files.write(outputPath, withBom);
    }

    private String formatSrt(List<SubtitleEntry> subtitles, boolean bilingual) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < subtitles.size(); i++) {
            SubtitleEntry sub = subtitles.get(i);
            String start = formatSrtTime(sub.getStartTime());
            String end = formatSrtTime(sub.getEndTime());

            String text;
            if (bilingual) {
                text = sub.getSourceText() + "\n" + sub.getTranslatedText();
            } else {
                text = sub.getTranslatedText();
            }

            sb.append(sub.getIndex()).append("\n");
            sb.append(start).append(" --> ").append(end).append("\n");
            sb.append(text).append("\n");
            if (i < subtitles.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String formatSrtTime(String timeStr) {
        if (timeStr.contains(",") || timeStr.contains(":")) {
            return timeStr;
        }
        try {
            double seconds = Double.parseDouble(timeStr);
            return secondsToSrtTime(seconds);
        } catch (NumberFormatException e) {
            return timeStr;
        }
    }

    public static String secondsToSrtTime(double seconds) {
        int hours = (int) (seconds / 3600);
        int minutes = (int) ((seconds % 3600) / 60);
        int secs = (int) (seconds % 60);
        int millis = (int) ((seconds % 1) * 1000);
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, secs, millis);
    }

    public List<SubtitleEntry> parseSrt(Path srtPath) throws Exception {
        String content = Files.readString(srtPath, StandardCharsets.UTF_8);
        String[] blocks = content.trim().split("\n\n");
        List<SubtitleEntry> entries = new ArrayList<>();

        for (String block : blocks) {
            String[] lines = block.trim().split("\n");
            if (lines.length < 3) continue;

            int index = Integer.parseInt(lines[0].trim());
            String timeLine = lines[1].trim();
            String[] times = timeLine.split(" --> ");
            String start = times[0].trim();
            String end = times[1].trim();

            String sourceText = "";
            String translatedText = "";
            if (lines.length >= 4) {
                sourceText = lines[2].trim();
                translatedText = lines[3].trim();
            } else if (lines.length == 3) {
                translatedText = lines[2].trim();
            }

            SubtitleEntry entry = new SubtitleEntry();
            entry.setIndex(index);
            entry.setStartTime(start);
            entry.setEndTime(end);
            entry.setSourceText(sourceText);
            entry.setTranslatedText(translatedText);
            entries.add(entry);
        }

        return entries;
    }
}
