import dashscope
from dashscope import Generation
from typing import Callable, Optional, List
from app.config import get_settings


class TranslatorService:
    def __init__(self, api_key: str, progress_callback: Optional[Callable[[float, str], None]] = None):
        dashscope.api_key = api_key
        self.progress_callback = progress_callback
        self.batch_size = 50
        self.model = get_settings().TRANSLATION_MODEL

    def translate(
        self,
        subtitles: List[dict],
        source_language: str,
        target_language: str,
    ) -> List[dict]:
        results = []
        total_batches = (len(subtitles) + self.batch_size - 1) // self.batch_size

        for i in range(0, len(subtitles), self.batch_size):
            batch = subtitles[i:i + self.batch_size]
            batch_num = i // self.batch_size + 1

            translated_texts = self._translate_batch(batch, source_language, target_language)

            for j, sub in enumerate(batch):
                translated = translated_texts[j] if j < len(translated_texts) else ""
                results.append({
                    "text": sub["text"],
                    "translated_text": translated,
                    "start_time": sub["start_time"],
                    "end_time": sub["end_time"],
                })

            if self.progress_callback:
                self.progress_callback(batch_num / total_batches, f"翻译进度 {batch_num}/{total_batches}")

        return results

    def _translate_batch(
        self,
        batch: List[dict],
        source_language: str,
        target_language: str,
    ) -> List[str]:
        texts = [sub["text"] for sub in batch]
        numbered_text = "\n".join(f"{i+1}. {t}" for i, t in enumerate(texts))

        prompt = (
            f"请将以下字幕文本从{source_language}翻译为{target_language}，"
            f"保持原文的语义和语气。每行一个字幕，保持编号格式（编号. 翻译内容），"
            f"不要添加额外解释：\n\n{numbered_text}"
        )

        for attempt in range(3):
            try:
                response = Generation.call(
                    model=self.model,
                    prompt=prompt,
                )

                if response.status_code == 200:
                    return self._parse_translated(response.output.text, len(batch))
                else:
                    raise Exception(f"Translation failed: {response.message}")
            except Exception as e:
                if attempt < 2:
                    import time
                    time.sleep(2 ** attempt)
                else:
                    raise

    def _parse_translated(self, text: str, expected_count: int) -> List[str]:
        lines = text.strip().split("\n")
        results = []
        for line in lines:
            line = line.strip()
            if not line:
                continue
            if ". " in line:
                parts = line.split(". ", 1)
                results.append(parts[1])
            else:
                results.append(line)

        while len(results) < expected_count:
            results.append("")

        return results[:expected_count]
