import uuid
from typing import Dict, Optional, Callable
from app.config import get_settings
from app.models.schemas import TaskStatus, TaskResult, SubtitleEntry, ProcessRequest
from app.services.downloader import Downloader
from app.services.audio_extractor import AudioExtractor
from app.services.speech_recognition import SpeechRecognitionService
from app.services.translator import TranslatorService
from app.services.subtitle_generator import SubtitleGenerator
from app.utils.file_manager import create_task_dir


class PipelineProcessor:
    def __init__(self):
        self.tasks: Dict[str, TaskStatus] = {}
        self.results: Dict[str, TaskResult] = {}
        self.progress_callbacks: Dict[str, Callable] = {}
        self.settings = get_settings()

    def register_progress_callback(self, task_id: str, callback: Callable):
        self.progress_callbacks[task_id] = callback

    def unregister_progress_callback(self, task_id: str):
        self.progress_callbacks.pop(task_id, None)

    def _notify_progress(self, task_id: str, stage: str, progress: float, message: str):
        if task_id in self.tasks:
            self.tasks[task_id].stage = stage
            self.tasks[task_id].progress = progress
            self.tasks[task_id].message = message
        if task_id in self.progress_callbacks:
            try:
                self.progress_callbacks[task_id](task_id, stage, progress, message)
            except Exception:
                pass

    def create_task(self, request: ProcessRequest) -> str:
        task_id = str(uuid.uuid4())[:8]
        self.tasks[task_id] = TaskStatus(
            task_id=task_id,
            stage="downloading",
            progress=0.0,
            message="任务已创建",
        )
        return task_id

    def get_task_status(self, task_id: str) -> Optional[TaskStatus]:
        return self.tasks.get(task_id)

    def get_task_result(self, task_id: str) -> Optional[TaskResult]:
        return self.results.get(task_id)

    def process(self, task_id: str, request: ProcessRequest):
        try:
            task_dir = create_task_dir(task_id)
            video_path = task_dir / "video.mp4"
            audio_path = task_dir / "audio.wav"
            srt_path = task_dir / "subtitles.srt"

            self._notify_progress(task_id, "downloading", 0.0, "开始下载视频")
            downloader = Downloader(
                output_dir=task_dir,
                progress_callback=lambda p, m: self._notify_progress(
                    task_id, "downloading", 0.0 + p * 0.25, m
                ),
            )
            downloaded_path = downloader.download(request.url)
            if downloaded_path != video_path:
                downloaded_path.rename(video_path)
            self._notify_progress(task_id, "downloading", 0.25, "视频下载完成")

            self._notify_progress(task_id, "extracting_audio", 0.25, "开始提取音频")
            extractor = AudioExtractor()
            extractor.extract(video_path, audio_path)
            self._notify_progress(task_id, "extracting_audio", 0.30, "音频提取完成")

            self._notify_progress(task_id, "recognizing", 0.30, "开始语音识别")
            recognizer = SpeechRecognitionService(
                api_key=self.settings.DASHSCOPE_API_KEY,
                progress_callback=lambda p, m: self._notify_progress(
                    task_id, "recognizing", 0.30 + p * 0.35, m
                ),
            )
            recognized = recognizer.recognize(audio_path, request.source_language)
            self._notify_progress(task_id, "recognizing", 0.65, "语音识别完成")

            self._notify_progress(task_id, "translating", 0.65, "开始翻译字幕")
            translator = TranslatorService(
                api_key=self.settings.DASHSCOPE_API_KEY,
                progress_callback=lambda p, m: self._notify_progress(
                    task_id, "translating", 0.65 + p * 0.25, m
                ),
            )
            translated = translator.translate(
                recognized, request.source_language, request.target_language
            )
            self._notify_progress(task_id, "translating", 0.90, "字幕翻译完成")

            self._notify_progress(task_id, "generating_subtitle", 0.90, "开始生成字幕文件")
            subtitle_entries = []
            for i, item in enumerate(translated, 1):
                subtitle_entries.append(SubtitleEntry(
                    index=i,
                    start_time=self._seconds_to_srt_time(item["start_time"]),
                    end_time=self._seconds_to_srt_time(item["end_time"]),
                    source_text=item["text"],
                    translated_text=item["translated_text"],
                ))
            generator = SubtitleGenerator()
            generator.generate_srt(subtitle_entries, srt_path, bilingual=True)
            self._notify_progress(task_id, "generating_subtitle", 1.00, "字幕文件生成完成")

            duration = extractor.get_duration(audio_path)
            result = TaskResult(
                video_path=str(video_path),
                srt_path=str(srt_path),
                subtitles=subtitle_entries,
                duration=duration,
            )
            self.results[task_id] = result
            self.tasks[task_id].result = result
            self._notify_progress(task_id, "completed", 1.0, "处理完成")

        except Exception as e:
            self._notify_progress(task_id, "failed", 0.0, f"处理失败: {str(e)}")

    def update_subtitles(self, task_id: str, subtitles: list) -> bool:
        if task_id not in self.results:
            return False
        result = self.results[task_id]
        result.subtitles = [SubtitleEntry(**s) for s in subtitles]
        task_dir = create_task_dir(task_id)
        srt_path = task_dir / "subtitles.srt"
        generator = SubtitleGenerator()
        generator.generate_srt(result.subtitles, srt_path, bilingual=True)
        result.srt_path = str(srt_path)
        return True

    @staticmethod
    def _seconds_to_srt_time(seconds: float) -> str:
        hours = int(seconds // 3600)
        minutes = int((seconds % 3600) // 60)
        secs = int(seconds % 60)
        millis = int((seconds % 1) * 1000)
        return f"{hours:02d}:{minutes:02d}:{secs:02d},{millis:03d}"


processor = PipelineProcessor()
