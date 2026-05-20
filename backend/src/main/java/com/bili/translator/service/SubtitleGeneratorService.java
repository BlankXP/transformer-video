package com.bili.translator.service;

import com.bili.translator.model.SubtitleEntry;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;

@Service
public class SubtitleGeneratorService {

    public Path generateSrt(List<SubtitleEntry> subtitles, Path outputPath, boolean bilingual) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < subtitles.size(); i++) {
            SubtitleEntry entry = subtitles.get(i);
            String text;
            if (bilingual) {
                text = entry.getSourceText() + "\n" + entry.getTranslatedText();
            } else {
                text = entry.getTranslatedText();
            }
            sb.append(i + 1)
              .append("\n")
              .append(secondsToSrtTime(entry.getStartTime()))
              .append(" --> ")
              .append(secondsToSrtTime(entry.getEndTime()))
              .append("\n")
              .append(text)
              .append("\n\n");
        }
        Files.writeString(outputPath, sb.toString(), StandardCharsets.UTF_8);
        return outputPath;
    }

    public List<SubtitleEntry> parseSrt(Path srtPath) throws Exception {
        String content = Files.readString(srtPath, StandardCharsets.UTF_8);
        String[] blocks = content.split("\n\n");
        List<SubtitleEntry> result = new ArrayList<>();
        for (String block : blocks) {
            if (block.isBlank()) {
                continue;
            }
            String[] lines = block.split("\n");
            int index = Integer.parseInt(lines[0].trim());
            String[] times = lines[1].split(" --> ");
            double startTime = srtTimeToSeconds(times[0].trim());
            double endTime = srtTimeToSeconds(times[1].trim());
            String sourceText;
            String translatedText;
            if (lines.length - 2 >= 2) {
                sourceText = lines[2];
                translatedText = lines[3];
            } else {
                sourceText = "";
                translatedText = lines[2];
            }
            SubtitleEntry entry = new SubtitleEntry();
            entry.setStartTime(startTime);
            entry.setEndTime(endTime);
            entry.setSourceText(sourceText);
            entry.setTranslatedText(translatedText);
            result.add(entry);
        }
        return result;
    }

    public static String secondsToSrtTime(double seconds) {
        int hours = (int) (seconds / 3600);
        int minutes = (int) ((seconds % 3600) / 60);
        int secs = (int) (seconds % 60);
        int millis = (int) ((seconds % 1) * 1000);
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, secs, millis);
    }

    private static double srtTimeToSeconds(String srtTime) {
        String[] parts = srtTime.split("[:,]");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        int secs = Integer.parseInt(parts[2]);
        int millis = Integer.parseInt(parts[3]);
        return hours * 3600 + minutes * 60 + secs + millis / 1000.0;
    }
}
