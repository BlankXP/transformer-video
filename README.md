# BiliTranslator — B站/YouTube 视频翻译字幕生成器

输入B站或 YouTube 视频链接,自动完成下载、语音识别、翻译、字幕生成与烧录的 Web 应用。

## 功能特性

- **视频下载**:支持B站 URL / BV 号、YouTube 链接输入(yt-dlp)
- **语音识别**:阿里云百炼 Paraformer,带时间戳
- **多语言翻译**:阿里云百炼 Qwen(默认 `qwen-plus`),可选 OpenRouter 渠道
- **字幕生成**:输出 SRT 双语字幕(原文 + 译文)
- **字幕烧录**:ffmpeg 烧录硬字幕到视频,内置 Noto CJK 字体
- **实时进度**:WebSocket 推送各阶段处理进度
- **在线编辑**:字幕预览、修正后可重新烧录
- **任务管理**:历史任务列表、查询去重、重试、删除
- **用户认证**:JWT 登录认证

## 处理流水线

```
用户输入 URL + 目标语言
  → yt-dlp 下载视频
  → ffmpeg 提取音频(16kHz 单声道)
  → Paraformer 语音识别(带时间戳)
  → Qwen 翻译字幕
  → 生成 SRT 字幕文件
  → ffmpeg 烧录硬字幕
  → 前端预览 / 下载(SRT + 视频)
```

各阶段进度通过 `WS /ws/{task_id}` 实时推送。

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | React 19 + TypeScript + Vite 8 + TailwindCSS 4 |
| 后端 | Java 17 + Spring Boot 3.2.5(Spring Web + WebSocket) |
| AI 服务 | 阿里云百炼 DashScope SDK(Paraformer ASR + Qwen)、OpenRouter(可选) |
| 认证 | JJWT(JSON Web Token) |
| 工具链 | yt-dlp(下载)、ffmpeg(音频提取/字幕烧录) |
| 部署 | Docker + docker-compose,前端 Nginx 托管 |

## 项目结构

```
├── backend/                  # Spring Boot 后端
│   ├── src/main/java/com/bili/translator/
│   │   ├── controller/       # Auth / Video / Subtitle 控制器
│   │   ├── service/          # 下载、音频提取、ASR、翻译、字幕生成、烧录
│   │   ├── pipeline/         # 流水线处理器与进度回调
│   │   ├── websocket/        # WebSocket 进度推送
│   │   └── config/           # JWT 过滤器、异步线程池、WebSocket 配置
│   ├── Dockerfile
│   └── .env.example          # 环境变量模板
├── frontend/                 # React 前端
│   ├── src/components/       # 登录、输入、进度、字幕编辑、播放器、下载
│   ├── src/hooks/            # useTask / useWebSocket
│   ├── src/api/              # API 客户端(JWT 携带)
│   ├── Dockerfile            # 多阶段构建:node 构建 + nginx 托管
│   └── nginx.conf            # 静态资源 + /api、/videos、/ws 反向代理
├── docker-compose.yml
└── docs/                     # 设计文档
```

## API 一览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录,返回 JWT |
| POST | `/api/video/process` | 提交处理任务(URL + 目标语言) |
| GET | `/api/video/tasks` | 任务历史列表 |
| GET | `/api/video/find?url=` | 查询 URL 是否已处理过 |
| GET | `/api/video/{taskId}/status` | 查询任务状态 |
| POST | `/api/video/{taskId}/retry` | 重试失败任务 |
| DELETE | `/api/video/{taskId}` | 删除任务 |
| GET | `/api/video/{taskId}/burned` | 下载烧录字幕后的视频 |
| GET | `/api/subtitle/{taskId}/srt` | 下载 SRT 字幕 |
| GET | `/api/subtitle/{taskId}/txt` | 下载识别文本 |
| PUT | `/api/subtitle/{taskId}` | 保存编辑后的字幕 |
| WS | `/ws/{taskId}` | 实时进度推送 |

## 本地开发

### 后端

```bash
cd backend
cp .env.example .env          # 填入 DASHSCOPE_API_KEY 等
mvn spring-boot:run           # 默认端口 8000
```

### 前端

```bash
cd frontend
npm install
npm run dev                   # 默认代理 /api、/ws 到 http://localhost:8000
```

代理目标在 `frontend/vite.config.ts` 中修改;也可通过 `VITE_API_BASE_URL` 环境变量直连后端地址。

## Docker 部署

```bash
# 1. 准备后端环境变量
cp backend/.env.example backend/.env
# 编辑 backend/.env,至少填入 DASHSCOPE_API_KEY

# 2. 构建并启动(后端 8000 端口,前端 80 端口,均为 host 网络)
docker compose up -d --build
```

启动后访问 `http://<服务器IP>/`,默认用户名 `admin`。

> **密码说明**:出于安全考虑,代码不内置默认密码。未配置 `AUTH_PASSWORD` 时,后端每次启动会生成随机密码并打印到日志(提示 `未配置 AUTH_PASSWORD,本次启动使用随机密码: ...`),建议在 `backend/.env` 中配置固定密码;`JWT_SECRET` 未配置时同样会生成随机密钥(重启后已登录用户需重新登录)。

> **CentOS 7 注意**:老内核(3.10)上 Docker 默认 seccomp 配置会阻止新版 nginx 写 PID 文件,导致前端容器反复重启。若遇到此问题,在 compose 的 `frontend` 服务中添加:
>
> ```yaml
>     security_opt:
>       - seccomp=unconfined
> ```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DASHSCOPE_API_KEY` | (必填) | 阿里云百炼 API Key |
| `OPENROUTER_API_KEY` | (可选) | OpenRouter API Key,配置后可用于翻译 |
| `OPENROUTER_MODEL` | `nvidia/nemotron-3-ultra-550b-a55b:free` | OpenRouter 翻译模型 |
| `TEMP_DIR` | `./temp` | 临时文件目录 |
| `MAX_VIDEO_DURATION` | `3600` | 视频最大时长(秒) |
| `AUDIO_SEGMENT_DURATION` | `300` | 长音频分段长度(秒) |
| `ASR_MODEL` | `paraformer-realtime-v2` | 语音识别模型 |
| `TRANSLATION_MODEL` | `qwen-plus` | 翻译模型 |
| `AUTH_USERNAME` | `admin` | 登录用户名 |
| `AUTH_PASSWORD` | (随机生成) | 登录密码,未配置时每次启动随机生成并打印到日志 |
| `JWT_SECRET` | (随机生成) | JWT 签名密钥,未配置时每次启动随机生成 |
| `JWT_EXPIRATION` | `86400000` | Token 有效期(毫秒) |
