from fastapi import APIRouter, HTTPException, BackgroundTasks
from fastapi.responses import FileResponse
from app.models.schemas import ProcessRequest
from app.pipeline.processor import processor
from app.api.websocket import create_ws_callback

router = APIRouter(prefix="/api/video", tags=["video"])


@router.post("/process")
async def process_video(request: ProcessRequest, background_tasks: BackgroundTasks):
    task_id = processor.create_task(request)
    processor.register_progress_callback(task_id, create_ws_callback(task_id))
    background_tasks.add_task(processor.process, task_id, request)
    return {"task_id": task_id}


@router.get("/{task_id}/status")
async def get_task_status(task_id: str):
    status = processor.get_task_status(task_id)
    if not status:
        raise HTTPException(status_code=404, detail="任务不存在")
    return status


@router.get("/{task_id}/download")
async def download_video(task_id: str):
    result = processor.get_task_result(task_id)
    if not result:
        raise HTTPException(status_code=404, detail="任务结果不存在")
    from pathlib import Path
    video_path = Path(result.video_path)
    if not video_path.exists():
        raise HTTPException(status_code=404, detail="视频文件不存在")
    return FileResponse(
        path=str(video_path),
        media_type="video/mp4",
        filename=f"{task_id}_subtitled.mp4",
    )


@router.post("/{task_id}/burn")
async def burn_video(task_id: str, background_tasks: BackgroundTasks):
    result = processor.get_task_result(task_id)
    if not result:
        raise HTTPException(status_code=404, detail="任务结果不存在")
    background_tasks.add_task(processor.burn_with_updated_subtitles, task_id)
    return {"task_id": task_id}
