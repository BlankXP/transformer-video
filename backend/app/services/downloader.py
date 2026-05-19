import yt_dlp
from pathlib import Path
from typing import Callable, Optional


class Downloader:
    def __init__(self, output_dir: Path, progress_callback: Optional[Callable[[float, str], None]] = None):
        self.output_dir = output_dir
        self.progress_callback = progress_callback

    def download(self, url: str) -> Path:
        if url.startswith("BV") or url.startswith("bv"):
            url = f"https://www.bilibili.com/video/{url}"

        ydl_opts = {
            "outtmpl": str(self.output_dir / "video.%(ext)s"),
            "format": "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
            "merge_output_format": "mp4",
            "progress_hooks": [self._progress_hook],
            "quiet": True,
            "no_warnings": True,
        }

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            filename = ydl.prepare_filename(info)
            video_path = Path(filename)
            if video_path.suffix != ".mp4":
                video_path = video_path.with_suffix(".mp4")
            return video_path

    def _progress_hook(self, d):
        if d["status"] == "downloading":
            if self.progress_callback:
                total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                downloaded = d.get("downloaded_bytes", 0)
                if total > 0:
                    progress = downloaded / total
                    self.progress_callback(progress, f"下载中 {downloaded}/{total} bytes")
        elif d["status"] == "finished":
            if self.progress_callback:
                self.progress_callback(1.0, "下载完成")
