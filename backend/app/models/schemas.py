from typing import Literal, Optional

from pydantic import BaseModel


class ProcessRequest(BaseModel):
    url: str
    source_language: str
    target_language: str


class SubtitleEntry(BaseModel):
    index: int
    start_time: str
    end_time: str
    source_text: str
    translated_text: str


class TaskResult(BaseModel):
    video_path: str
    srt_path: str
    subtitles: list[SubtitleEntry]
    duration: float


class TaskStatus(BaseModel):
    task_id: str
    stage: Literal[
        "downloading",
        "extracting_audio",
        "recognizing",
        "translating",
        "generating_subtitle",
        "burning_subtitle",
        "completed",
        "failed",
    ]
    progress: float = 0.0
    message: str = ""
    result: Optional[TaskResult] = None
