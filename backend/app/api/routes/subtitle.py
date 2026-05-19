from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse
from app.models.schemas import SubtitleEntry
from app.pipeline.processor import processor
from typing import List

router = APIRouter(prefix="/api/subtitle", tags=["subtitle"])


@router.get("/{task_id}/srt")
async def download_srt(task_id: str):
    result = processor.get_task_result(task_id)
    if not result:
        raise HTTPException(status_code=404, detail="任务结果不存在")
    from pathlib import Path
    srt_path = Path(result.srt_path)
    if not srt_path.exists():
        raise HTTPException(status_code=404, detail="字幕文件不存在")
    return FileResponse(
        path=str(srt_path),
        media_type="text/plain",
        filename=f"{task_id}_subtitles.srt",
    )


@router.put("/{task_id}")
async def save_subtitle(task_id: str, subtitles: List[SubtitleEntry]):
    success = processor.update_subtitles(task_id, [s.model_dump() for s in subtitles])
    if not success:
        raise HTTPException(status_code=404, detail="任务结果不存在")
    return {"message": "字幕已保存"}
