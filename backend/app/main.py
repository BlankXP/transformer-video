import shutil
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from app.config import get_settings
from app.api.routes import video, subtitle
from app.api.websocket import websocket_endpoint
from app.utils.file_manager import cleanup_expired_tasks
from pathlib import Path


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    if not shutil.which("ffmpeg"):
        raise RuntimeError("ffmpeg 未安装，请先安装 ffmpeg")
    temp_dir = Path(settings.TEMP_DIR)
    temp_dir.mkdir(parents=True, exist_ok=True)
    cleanup_expired_tasks()
    yield


app = FastAPI(title="B站视频翻译字幕生成器", version="1.0.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(video.router)
app.include_router(subtitle.router)

app.websocket("/ws/{task_id}")(websocket_endpoint)

settings = get_settings()
temp_dir = Path(settings.TEMP_DIR)
temp_dir.mkdir(parents=True, exist_ok=True)
app.mount("/videos", StaticFiles(directory=str(temp_dir)), name="videos")
