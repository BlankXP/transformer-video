from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    DASHSCOPE_API_KEY: str = ""
    TEMP_DIR: str = "./temp"
    MAX_VIDEO_DURATION: int = 3600
    AUDIO_SEGMENT_DURATION: int = 300

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


settings = Settings()


def get_settings() -> Settings:
    return settings
