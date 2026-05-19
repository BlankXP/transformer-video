# B站视频下载与翻译字幕生成器 — 设计文档

## 概述

一个 Web 应用，支持下载B站视频、使用阿里云百炼进行语音识别和翻译、生成多语言字幕（SRT 格式）并烧录硬字幕到视频中。

## 需求

- 输入B站视频 URL 或 BV 号，下载视频
- 使用阿里云百炼 Paraformer 进行语音识别（带时间戳）
- 使用阿里云百炼 Qwen 进行多语言翻译
- 生成 SRT 字幕文件
- 使用 ffmpeg 将字幕烧录到视频中（硬字幕）
- 支持多语言互译（用户选择源语言和目标语言）
- WebSocket 实时进度推送
- 字幕在线预览与编辑

## 架构：同步流水线

选择同步流水线架构，各步骤串行执行，通过 WebSocket 实时推送进度到前端。

### 数据流

```
用户输入 URL + 语言选择
    → yt-dlp 下载视频
    → ffmpeg 提取音频
    → 百炼 Paraformer 语音识别（带时间戳）
    → 百炼 Qwen 翻译字幕
    → 生成 SRT 字幕文件
    → ffmpeg 烧录硬字幕
    → 返回结果（SRT + 视频）
```

全程通过 WebSocket 推送各步骤进度。

## 后端设计

### 技术栈

- Python 3.11+
- FastAPI
- yt-dlp（B站视频下载）
- ffmpeg-python（音频提取、字幕烧录）
- dashscope SDK（百炼 Paraformer + Qwen）
- uvicorn（ASGI 服务器）

### 项目结构

```
backend/
├── app/
│   ├── main.py
│   ├── config.py
│   ├── api/
│   │   ├── routes/
│   │   │   ├── video.py
│   │   │   └── subtitle.py
│   │   └── websocket.py
│   ├── services/
│   │   ├── downloader.py
│   │   ├── audio_extractor.py
│   │   ├── speech_recognition.py
│   │   ├── translator.py
│   │   ├── subtitle_generator.py
│   │   └── video_burner.py
│   ├── pipeline/
│   │   └── processor.py
│   ├── models/
│   │   └── schemas.py
│   └── utils/
│       └── file_manager.py
├── requirements.txt
└── .env
```

### API 设计

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/video/process` | 提交处理任务（URL + 源语言 + 目标语言） |
| GET | `/api/video/{task_id}/status` | 查询任务状态 |
| GET | `/api/subtitle/{task_id}/srt` | 下载 SRT 字幕文件 |
| GET | `/api/video/{task_id}/download` | 下载带硬字幕的视频 |
| PUT | `/api/subtitle/{task_id}` | 保存编辑后的字幕内容 |
| POST | `/api/video/{task_id}/burn` | 用编辑后的字幕重新烧录视频 |
| WS | `/ws/{task_id}` | WebSocket 实时进度推送 |

### 数据模型

```python
class ProcessRequest:
    url: str
    source_language: str
    target_language: str

class TaskStatus:
    task_id: str
    stage: Literal[
        "downloading", "extracting_audio", "recognizing",
        "translating", "generating_subtitle", "burning_subtitle",
        "completed", "failed"
    ]
    progress: float  # 0.0 ~ 1.0
    message: str
    result: Optional[TaskResult]

class SubtitleEntry:
    index: int
    start_time: str  # SRT 时间格式
    end_time: str
    source_text: str
    translated_text: str

class TaskResult:
    video_path: str
    srt_path: str
    subtitles: list[SubtitleEntry]
    duration: float
```

### 核心服务

#### downloader.py
- 使用 yt-dlp 下载B站视频
- 支持 URL 和 BV 号两种输入
- 自动选择最佳视频质量
- 下载进度回调，用于推送 WebSocket 进度

#### audio_extractor.py
- 使用 ffmpeg 从视频中提取音频为 WAV 格式
- 采样率 16kHz，单声道（百炼 ASR 要求）

#### speech_recognition.py
- 调用百炼 Paraformer API
- 长音频（> 5 分钟）先上传文件获取 URL，再调用异步识别
- 短音频直接调用同步识别
- 返回带时间戳的识别结果

#### translator.py
- 调用百炼 Qwen 大模型翻译
- Prompt：将字幕文本从源语言翻译为目标语言，保持语义和语气
- 分批翻译：每批最多 50 行字幕
- 保持时间戳对应关系

#### subtitle_generator.py
- 将识别结果 + 翻译结果组装为 SRT 格式
- 支持双语字幕（源语言 + 目标语言）

#### video_burner.py
- 使用 ffmpeg 将 SRT 字幕烧录到视频
- 字体大小、颜色、位置可配置

### 进度状态

流水线各阶段对应的状态和进度范围：

| 阶段 | 状态 | 进度范围 |
|------|------|----------|
| 下载视频 | downloading | 0.00 ~ 0.20 |
| 提取音频 | extracting_audio | 0.20 ~ 0.25 |
| 语音识别 | recognizing | 0.25 ~ 0.55 |
| 翻译字幕 | translating | 0.55 ~ 0.75 |
| 生成字幕 | generating_subtitle | 0.75 ~ 0.80 |
| 烧录字幕 | burning_subtitle | 0.80 ~ 1.00 |

## 前端设计

### 技术栈

- React 18 + TypeScript
- Vite
- Tailwind CSS

### 项目结构

```
frontend/
├── src/
│   ├── App.tsx
│   ├── components/
│   │   ├── VideoInput.tsx
│   │   ├── ProgressPanel.tsx
│   │   ├── SubtitleEditor.tsx
│   │   ├── VideoPlayer.tsx
│   │   └── DownloadPanel.tsx
│   ├── hooks/
│   │   ├── useWebSocket.ts
│   │   └── useTask.ts
│   ├── api/
│   │   └── client.ts
│   └── types/
│       └── index.ts
├── package.json
└── vite.config.ts
```

### 页面布局

顶部为标题栏，下方分为四个区域：

1. **输入区**：URL 输入框、源语言/目标语言选择、开始处理按钮
2. **进度与预览区**（左右分栏）：左侧为步骤进度面板，右侧为视频预览播放器
3. **字幕编辑区**：可预览和编辑字幕内容（源文本 + 翻译文本）
4. **下载区**：下载 SRT 字幕文件、下载带硬字幕的视频

### 关键交互

1. 用户粘贴B站链接 → 自动识别 BV 号
2. 选择源语言和目标语言 → 点击开始处理
3. WebSocket 实时更新各步骤进度
4. 完成后可在线预览视频（处理中播放原始视频，烧录完成后播放带字幕视频）和字幕
5. 字幕可编辑修正
6. 下载 SRT 文件或带硬字幕的视频

## 错误处理

| 场景 | 处理方式 |
|------|----------|
| B站视频下载失败 | 提示用户检查链接有效性，支持重试 |
| 百炼 API 调用失败 | 自动重试 3 次（指数退避），仍失败则标记任务失败 |
| 长音频识别超时 | 将音频分段（每段 ≤ 5 分钟），逐段识别后合并 |
| 翻译质量不佳 | 用户可在字幕编辑器中手动修正 |
| ffmpeg 处理失败 | 检查 ffmpeg 是否安装，给出安装指引 |
| 磁盘空间不足 | 处理前检查可用空间，不足时提前报错 |

## 配置管理

```env
DASHSCOPE_API_KEY=sk-xxx
TEMP_DIR=./workspace/temp
MAX_VIDEO_DURATION=3600
AUDIO_SEGMENT_DURATION=300
```

## 临时文件管理

- 所有中间文件存放在 `workspace/temp/{task_id}/` 目录
- 任务完成后保留最终结果 24 小时
- 定期清理过期临时文件

## 百炼 API 集成细节

### 语音识别 (Paraformer)

- 使用 dashscope SDK 的 SpeechRecognizer
- 长音频：上传音频文件获取 URL → 调用异步识别接口 → 轮询结果
- 返回结果包含时间戳，可直接用于 SRT 生成
- 支持语言：中文、英文、日文等

### 翻译 (Qwen)

- 使用 dashscope SDK 调用 Qwen 大模型
- Prompt 模板：`请将以下字幕文本从{源语言}翻译为{目标语言}，保持原文的语义和语气，每行一个字幕，不要添加额外解释：\n\n{text}`
- 分批翻译：每批最多 50 行字幕，避免超出 token 限制
- 保持时间戳对应关系
