import ffmpeg
from pathlib import Path


class AudioExtractor:
    def extract(self, video_path: Path, output_path: Path) -> Path:
        (
            ffmpeg
            .input(str(video_path))
            .output(
                str(output_path),
                ar=16000,
                ac=1,
                format="wav"
            )
            .overwrite_output()
            .run(quiet=True)
        )
        return output_path

    def get_duration(self, audio_path: Path) -> float:
        probe = ffmpeg.probe(str(audio_path))
        duration = float(probe["format"]["duration"])
        return duration
