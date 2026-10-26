# BiliTranslator — B站/YouTube 视频翻译字幕生成器

输入 B站或 YouTube 视频链接，自动完成下载、语音识别、翻译、字幕生成与烧录的 Web 应用。

## 功能特性

- **视频下载**：支持 B站 URL / BV 号、YouTube 链接输入（yt-dlp）
- **语音识别**：阿里云百炼 Paraformer，带时间戳
- **多语言翻译**：阿里云百炼 Qwen（默认 `qwen-plus`），可选 OpenRouter 渠道
- **字幕生成**：输出 SRT 双语字幕（原文 + 译文）
- **字幕烧录**：ffmpeg 烧录硬字幕到视频，内置 Noto CJK 字体
- **实时进度**：WebSocket 推送各阶段处理进度
- **在线编辑**：字幕预览、修正后可重新烧录
- **任务管理**：历史任务列表、查询去重、重试、删除
- **用户认证**：JWT 登录认证
- **单容器部署**：前端构建产物打进后端 jar，一个进程同时提供页面、API 与 WebSocket

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

各阶段进度通过 `WS /ws/{taskId}` 实时推送。

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | React 19 + TypeScript + Vite 8 + TailwindCSS 4 |
| 后端 | Java 17 + Spring Boot 3.2.5（Spring Web + WebSocket） |
| AI 服务 | 阿里云百炼 DashScope SDK（Paraformer ASR + Qwen）、OpenRouter（可选） |
| 认证 | JJWT（JSON Web Token） |
| 工具链 | yt-dlp（下载）、ffmpeg（音频提取 / 字幕烧录） |
| 部署 | Docker + docker-compose，单容器由 Spring Boot 托管前端静态资源 |

## 项目结构

```
├── backend/                  # Spring Boot 后端（同时托管前端页面）
│   ├── src/main/java/com/bili/translator/
│   │   ├── controller/       # Auth / Video / Subtitle 控制器
│   │   ├── service/          # 下载、音频提取、ASR、翻译、字幕生成、烧录
│   │   ├── pipeline/         # 流水线处理器
│   │   ├── websocket/        # WebSocket 进度推送
│   │   ├── config/           # JWT 过滤器、静态资源、异步线程池、WebSocket
│   │   └── util/             # 子进程执行器、URL 校验、Range 解析
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── static/           # 前端构建产物（本地打包时生成，已 gitignore）
│   └── Dockerfile            # 多阶段：node 构建前端 → maven 打包 → JRE 运行
├── frontend/                 # React 源码（日常开发用 vite dev）
├── docker-compose.yml        # 单容器，80 端口
└── .dockerignore
```

## API 一览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录，返回 JWT |
| POST | `/api/video/process` | 提交处理任务（URL + 目标语言） |
| GET | `/api/video/tasks` | 任务历史列表 |
| GET | `/api/video/find?url=` | 查询 URL 是否已处理过 |
| GET | `/api/video/{taskId}/status` | 查询任务状态 |
| POST | `/api/video/{taskId}/retry` | 重试失败任务 |
| DELETE | `/api/video/{taskId}` | 删除任务 |
| GET | `/api/video/{taskId}/stream` | 视频预览（支持 Range）。**免登录**：`<video>` 标签无法附加请求头，靠 122 bit UUID 不可枚举兜底 |
| POST | `/api/download-ticket` | 申请一次性下载票据（需登录），body：`{"task_id","kind"}`，kind 取 `video/burned/srt/txt` |
| GET | `/api/download/{ticket}` | 凭票据下载。**免登录**：票据 120 秒有效、用一次即失效，浏览器直链流式下载 |
| GET | `/api/video/{taskId}/download` | 下载原始视频（未烧录），需登录 |
| GET | `/api/video/{taskId}/burned` | 下载烧录字幕后的视频 |
| GET | `/api/subtitle/{taskId}/srt` | 下载 SRT 字幕 |
| GET | `/api/subtitle/{taskId}/txt` | 下载识别文本 |
| PUT | `/api/subtitle/{taskId}` | 保存编辑后的字幕 |
| WS | `/ws/{taskId}?token=` | 实时进度推送（浏览器 WebSocket 无法带头，令牌走查询参数） |

除 `/api/auth/**`、`/api/video/{taskId}/stream` 与 `/api/download/{ticket}` 外，所有 `/api` 接口都需要 `Authorization: Bearer <token>`。
后两者免登录的原因不同：`<video>` 标签和 `<a href>` 直链都无法附加请求头，前者靠不可枚举的 UUID 兜底，后者靠短时一次性票据兜底。

## 本地开发

### 前后端分开跑（日常开发）

```bash
cd backend
cp .env.example .env          # 填入 DASHSCOPE_API_KEY、AUTH_PASSWORD、JWT_SECRET
mvn spring-boot:run           # 默认端口 8000

cd frontend
npm install
npm run dev                   # 默认代理 /api、/ws 到 http://localhost:8000
```

代理目标在 `frontend/vite.config.ts` 中修改；也可通过 `VITE_API_BASE_URL` 环境变量直连后端地址。

### 本地验证单 jar（模拟线上形态）

```bash
cd frontend && npm run build
mkdir -p ../backend/src/main/resources/static
cp -r dist/* ../backend/src/main/resources/static/

cd ../backend && mvn package -DskipTests
java -jar target/bili-translator-0.0.1-SNAPSHOT.jar   # 访问 http://localhost:8000
```

`static/` 是构建产物，已在 `.gitignore` 中忽略；每次改前端都要重新拷一次。

## Docker 部署

```bash
# 1. 准备环境变量
cp backend/.env.example backend/.env
# 编辑 backend/.env，至少填入 DASHSCOPE_API_KEY、AUTH_PASSWORD、JWT_SECRET

# 2. 构建并启动（单容器，宿主机 80 端口）
docker compose up -d --build
```

启动后访问 `http://<服务器IP>/`，默认用户名 `admin`。

> **凭据是必填项**：`AUTH_PASSWORD`（≥ 12 位）与 `JWT_SECRET`（≥ 32 字节）**没有默认值**，
> 缺失或强度不足时 `StartupConfigValidator` 会直接**拒绝启动**并打印原因，
> 不会再"生成随机密码"。请务必在 `backend/.env` 中配置。

> **镜像构建**：Dockerfile 是多阶段的——先用 Node 构建前端、再用 Maven 把产物打进 jar，
> 因此构建上下文必须是仓库根目录（已在 `docker-compose.yml` 中配好），
> 不要单独在 `backend/` 下 `docker build`。

> **CentOS 7 注意**：老内核（3.10）上 Docker 默认 seccomp 配置会阻止新版程序，若容器反复重启，
> 在 compose 的 `backend` 服务中添加：
>
> ```yaml
>     security_opt:
>       - seccomp=unconfined
> ```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DASHSCOPE_API_KEY` | （必填） | 阿里云百炼 API Key |
| `OPENROUTER_API_KEY` | （可选） | OpenRouter API Key，配置后可作为翻译回退 |
| `OPENROUTER_MODEL` | `nvidia/nemotron-3-ultra-550b-a55b:free` | OpenRouter 翻译模型 |
| `TEMP_DIR` | `./temp` | 临时文件目录 |
| `MAX_VIDEO_DURATION` | `3600` | 视频最大时长（秒） |
| `AUDIO_SEGMENT_DURATION` | `300` | 超过该时长才启用静音分段识别（真实分段边界由 silencedetect 决定） |
| `ASR_MODEL` | `paraformer-realtime-v2` | 语音识别模型 |
| `ASR_PUSH_INTERVAL_MS` | `20` | ASR 音频推送间隔（毫秒）。`100` = 1:1 实时（长视频最大瓶颈），`20` ≈ 5 倍速，`0` = 尽快推完；推太快若报错可调回 50~100 |
| `TRANSLATION_MODEL` | `qwen-plus` | 翻译模型 |
| `AUTH_USERNAME` | `admin` | 登录用户名 |
| `AUTH_PASSWORD` | （必填，≥ 12 位） | 登录密码，缺失或过短时拒绝启动 |
| `JWT_SECRET` | （必填，≥ 32 字节） | JWT 签名密钥，缺失或含示例特征时拒绝启动 |
| `JWT_EXPIRATION` | `86400000` | Token 有效期（毫秒，上限 7 天） |
| `TASK_RETENTION_HOURS` | `24` | 任务与文件保留时长，超期由定时任务清理 |
| `TRUST_PROXY_HEADERS` | `false` | 仅当应用前有可信反向代理覆盖写入 `X-Real-IP` 时开启，否则登录限流键可被伪造 |
| `ALLOWED_ORIGINS` | （空） | 前后端分离部署时允许的跨域来源（逗号分隔）。同源单容器保持为空，不产生任何 CORS 头 |
