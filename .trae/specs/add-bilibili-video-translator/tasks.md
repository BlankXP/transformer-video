# Tasks

- [x] Task 1: 搭建 FastAPI 后端项目骨架
  - [x] SubTask 1.1: 创建 backend/ 目录结构（app/main.py, config.py, requirements.txt, .env.example）
  - [x] SubTask 1.2: 实现 config.py 配置管理（读取环境变量：DASHSCOPE_API_KEY, TEMP_DIR, MAX_VIDEO_DURATION, AUDIO_SEGMENT_DURATION）
  - [x] SubTask 1.3: 实现 Pydantic 数据模型（ProcessRequest, TaskStatus, SubtitleEntry, TaskResult）
  - [x] SubTask 1.4: 实现 file_manager.py 临时文件管理（创建 task 目录、清理过期文件）
  - [x] SubTask 1.5: 启动 FastAPI 应用，验证 /docs 可访问

- [x] Task 2: 实现视频下载服务
  - [x] SubTask 2.1: 实现 downloader.py，使用 yt-dlp 下载B站视频，支持 URL 和 BV 号输入
  - [x] SubTask 2.2: 实现下载进度回调，用于推送 WebSocket 进度

- [x] Task 3: 实现音频提取服务
  - [x] SubTask 3.1: 实现 audio_extractor.py，使用 ffmpeg-python 从视频中提取 WAV 音频（16kHz，单声道）

- [x] Task 4: 实现语音识别服务
  - [x] SubTask 4.1: 实现 speech_recognition.py，集成百炼 Paraformer API
  - [x] SubTask 4.2: 实现长音频分段逻辑（> 5 分钟分段，逐段识别后合并）
  - [x] SubTask 4.3: 实现 API 调用失败自动重试（3 次，指数退避）

- [x] Task 5: 实现翻译服务
  - [x] SubTask 5.1: 实现 translator.py，集成百炼 Qwen API
  - [x] SubTask 5.2: 实现分批翻译逻辑（每批最多 50 行字幕）
  - [x] SubTask 5.3: 设计翻译 Prompt 模板，保持时间戳对应关系

- [x] Task 6: 实现字幕生成服务
  - [x] SubTask 6.1: 实现 subtitle_generator.py，将识别+翻译结果组装为 SRT 格式
  - [x] SubTask 6.2: 支持双语字幕输出（源语言 + 目标语言）

- [x] Task 7: 实现硬字幕烧录服务
  - [x] SubTask 7.1: 实现 video_burner.py，使用 ffmpeg 将 SRT 烧录到视频
  - [x] SubTask 7.2: 支持字体大小、颜色、位置配置

- [x] Task 8: 实现流水线编排与 WebSocket 进度推送
  - [x] SubTask 8.1: 实现 processor.py，串联下载→提取→识别→翻译→生成字幕→烧录流水线
  - [x] SubTask 8.2: 实现 websocket.py，WebSocket 实时推送各阶段进度
  - [x] SubTask 8.3: 实现任务状态内存管理（task_id → TaskStatus 映射）

- [x] Task 9: 实现 REST API 路由
  - [x] SubTask 9.1: 实现 POST /api/video/process（提交处理任务）
  - [x] SubTask 9.2: 实现 GET /api/video/{task_id}/status（查询任务状态）
  - [x] SubTask 9.3: 实现 GET /api/subtitle/{task_id}/srt（下载 SRT 文件）
  - [x] SubTask 9.4: 实现 GET /api/video/{task_id}/download（下载带硬字幕视频）
  - [x] SubTask 9.5: 实现 PUT /api/subtitle/{task_id}（保存编辑后字幕）
  - [x] SubTask 9.6: 实现 POST /api/video/{task_id}/burn（用编辑后字幕重新烧录）

- [x] Task 10: 搭建 React 前端项目骨架
  - [x] SubTask 10.1: 使用 Vite 创建 React + TypeScript 项目
  - [x] SubTask 10.2: 安装配置 Tailwind CSS
  - [x] SubTask 10.3: 定义 TypeScript 类型（TaskStatus, SubtitleEntry 等）
  - [x] SubTask 10.4: 实现 API 客户端封装（api/client.ts）

- [x] Task 11: 实现前端核心组件
  - [x] SubTask 11.1: 实现 VideoInput 组件（URL 输入、语言选择、开始处理按钮）
  - [x] SubTask 11.2: 实现 useWebSocket hook（WebSocket 连接管理）
  - [x] SubTask 11.3: 实现 useTask hook（任务状态管理）
  - [x] SubTask 11.4: 实现 ProgressPanel 组件（步骤进度展示）
  - [x] SubTask 11.5: 实现 VideoPlayer 组件（视频预览播放）
  - [x] SubTask 11.6: 实现 SubtitleEditor 组件（字幕预览与编辑）
  - [x] SubTask 11.7: 实现 DownloadPanel 组件（SRT/视频下载按钮）

- [x] Task 12: 组装前端页面
  - [x] SubTask 12.1: 在 App.tsx 中组装所有组件，实现完整页面布局
  - [x] SubTask 12.2: 实现完整交互流程（输入→处理→预览→编辑→下载）

- [x] Task 13: 端到端集成测试
  - [x] SubTask 13.1: 启动前后端，测试完整流程（输入URL→下载→识别→翻译→生成字幕→烧录→下载）
  - [x] SubTask 13.2: 测试错误场景（无效URL、API失败、磁盘不足）

# Task Dependencies
- [Task 2] depends on [Task 1]
- [Task 3] depends on [Task 1]
- [Task 4] depends on [Task 1]
- [Task 5] depends on [Task 1]
- [Task 6] depends on [Task 4, Task 5]
- [Task 7] depends on [Task 6]
- [Task 8] depends on [Task 2, Task 3, Task 4, Task 5, Task 6, Task 7]
- [Task 9] depends on [Task 8]
- [Task 11] depends on [Task 10]
- [Task 12] depends on [Task 11, Task 9]
- [Task 13] depends on [Task 12]
- [Task 2, Task 3] 可并行
- [Task 4, Task 5] 可并行
- [Task 10] 与 [Task 1~9] 可并行
