from pathlib import Path
from typing import List

from app.models.schemas import SubtitleEntry


class SubtitleGenerator:
    def generate_srt(
        self,
        subtitles: List[SubtitleEntry],
        output_path: Path,
        bilingual: bool = True,
    ) -> Path:
        srt_content = self._format_srt(subtitles, bilingual)
        output_path.write_text(srt_content, encoding="utf-8")
        return output_path

    def _format_srt(self, subtitles: List[SubtitleEntry], bilingual: bool) -> str:
        blocks = []
        for sub in subtitles:
            start = self._format_srt_time(sub.start_time)
            end = self._format_srt_time(sub.end_time)

            if bilingual:
                text = f"{sub.source_text}\n{sub.translated_text}"
            else:
                text = sub.translated_text

            block = f"{sub.index}\n{start} --> {end}\n{text}\n"
            blocks.append(block)

        return "\n".join(blocks)

    @staticmethod
    def _format_srt_time(time_str: str) -> str:
        if "," in time_str or ":" in time_str:
            return time_str

        try:
            seconds = float(time_str)
        except ValueError:
            return time_str

        hours = int(seconds // 3600)
        minutes = int((seconds % 3600) // 60)
        secs = int(seconds % 60)
        millis = int((seconds % 1) * 1000)
        return f"{hours:02d}:{minutes:02d}:{secs:02d},{millis:03d}"

    def parse_srt(self, srt_path: Path) -> List[SubtitleEntry]:
        content = srt_path.read_text(encoding="utf-8")
        blocks = content.strip().split("\n\n")
        entries = []

        for block in blocks:
            lines = block.strip().split("\n")
            if len(lines) < 3:
                continue

            index = int(lines[0])
            time_line = lines[1]
            start, end = time_line.split(" --> ")

            text_lines = lines[2:]
            if len(text_lines) >= 2:
                source_text = text_lines[0]
                translated_text = text_lines[1]
            else:
                source_text = ""
                translated_text = text_lines[0] if text_lines else ""

            entries.append(SubtitleEntry(
                index=index,
                start_time=start.strip(),
                end_time=end.strip(),
                source_text=source_text,
                translated_text=translated_text,
            ))

        return entries
