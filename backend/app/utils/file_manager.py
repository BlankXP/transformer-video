import shutil
import time
from pathlib import Path

from app.config import settings


def create_task_dir(task_id: str) -> Path:
    task_dir = Path(settings.TEMP_DIR) / task_id
    task_dir.mkdir(parents=True, exist_ok=True)
    return task_dir


def get_task_dir(task_id: str) -> Path:
    return Path(settings.TEMP_DIR) / task_id


def cleanup_expired_tasks(max_age_hours: int = 24) -> None:
    temp_dir = Path(settings.TEMP_DIR)
    if not temp_dir.exists():
        return
    now = time.time()
    max_age_seconds = max_age_hours * 3600
    for task_dir in temp_dir.iterdir():
        if task_dir.is_dir():
            dir_mtime = task_dir.stat().st_mtime
            if now - dir_mtime > max_age_seconds:
                shutil.rmtree(task_dir, ignore_errors=True)
