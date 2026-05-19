import dashscope
from dashscope.audio.asr import Recognition, RecognitionCallback, RecognitionResult
from pathlib import Path
from typing import Callable, Optional, List
import time
import json


class SpeechRecognitionService:
    def __init__(self, api_key: str, progress_callback: Optional[Callable[[float, str], None]] = None):
        dashscope.api_key = api_key
        self.progress_callback = progress_callback
        self.segment_duration = 300
        self.model = get_settings().ASR_MODEL

    def recognize(self, audio_path: Path, language: str = "zh") -> List[dict]:
        from app.config import get_settings
        settings = get_settings()

        duration = self._get_audio_duration(audio_path)

        if duration <= self.segment_duration:
            results = self._recognize_single(audio_path, language)
        else:
            results = self._recognize_long(audio_path, language, duration)

        return results

    def _recognize_single(self, audio_path: Path, language: str) -> List[dict]:
        for attempt in range(3):
            try:
                recognition = Recognition(
                    model=self.model,
                    format="wav",
                    sample_rate=16000,
                    language_hint=language,
                    audio=open(str(audio_path), "rb"),
                )
                result = recognition.call()

                if result.status_code == 200:
                    return self._parse_result(result)
                else:
                    raise Exception(f"Recognition failed: {result.message}")
            except Exception as e:
                if attempt < 2:
                    time.sleep(2 ** attempt)
                else:
                    raise

    def _recognize_long(self, audio_path: Path, language: str, duration: float) -> List[dict]:
        import subprocess

        all_results = []
        segments = int(duration / self.segment_duration) + 1

        for i in range(segments):
            start = i * self.segment_duration
            segment_path = audio_path.parent / f"segment_{i}.wav"

            subprocess.run([
                "ffmpeg", "-y", "-i", str(audio_path),
                "-ss", str(start), "-t", str(self.segment_duration),
                "-ar", "16000", "-ac", "1",
                str(segment_path)
            ], capture_output=True)

            if not segment_path.exists():
                continue

            results = self._recognize_single(segment_path, language)

            for r in results:
                r["start_time"] += start
                r["end_time"] += start

            all_results.extend(results)
            segment_path.unlink()

            if self.progress_callback:
                self.progress_callback((i + 1) / segments, f"识别进度 {i+1}/{segments}")

        return all_results

    def _parse_result(self, result) -> List[dict]:
        results = []
        if hasattr(result, "output") and result.output:
            sentences = result.output.get("sentence", []) if isinstance(result.output, dict) else []
            for sentence in sentences:
                results.append({
                    "text": sentence.get("text", ""),
                    "start_time": sentence.get("begin_time", 0) / 1000.0,
                    "end_time": sentence.get("end_time", 0) / 1000.0,
                })
        return results

    def _get_audio_duration(self, audio_path: Path) -> float:
        import ffmpeg
        probe = ffmpeg.probe(str(audio_path))
        return float(probe["format"]["duration"])
