import ffmpeg
from pathlib import Path


class VideoBurner:
    def __init__(
        self,
        font_size: int = 24,
        font_color: str = "white",
        border_color: str = "black",
        border_width: int = 2,
        position: int = 10,
    ):
        self.font_size = font_size
        self.font_color = font_color
        self.border_color = border_color
        self.border_width = border_width
        self.position = position

    def burn_subtitles(
        self,
        video_path: Path,
        srt_path: Path,
        output_path: Path,
    ) -> Path:
        subtitle_filter = (
            f"subtitles={str(srt_path)}:force_style="
            f"'FontSize={self.font_size},"
            f"PrimaryColour=&H{self._color_to_ass(self.font_color)},"
            f"OutlineColour=&H{self._color_to_ass(self.border_color)},"
            f"Outline={self.border_width},"
            f"MarginV={self.position}'"
        )

        (
            ffmpeg
            .input(str(video_path))
            .output(
                str(output_path),
                vf=subtitle_filter,
                codec="libx264",
                preset="medium",
                crf=23,
            )
            .overwrite_output()
            .run(quiet=True)
        )

        return output_path

    @staticmethod
    def _color_to_ass(color: str) -> str:
        color_map = {
            "white": "FFFFFF",
            "black": "000000",
            "yellow": "00FFFF",
            "red": "0000FF",
            "green": "00FF00",
            "blue": "FF0000",
        }
        return color_map.get(color.lower(), "FFFFFF")
