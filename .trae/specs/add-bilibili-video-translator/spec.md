# B站视频下载与翻译字幕生成器 Spec

## Why
用户需要一个工具来下载B站视频，自动识别视频中的语音内容，翻译成目标语言，并生成字幕文件和带硬字幕的视频，解决跨语言理解B站视频的需求。

## What Changes
- 新建 FastAPI 后端项目，包含视频下载、音频提取、语音识别、翻译、字幕生成、字幕烧录六个核心服务
- 新建 React 前端项目，包含视频输入、进度展示、字幕编辑、视频预览、文件下载五个核心组件
- 集成阿里云百炼 Paraformer（语音识别）和 Qwen（翻译）API
- 实现 WebSocket 实时进度推送
- 支持 SRT 字幕文件输出和硬字幕烧录

## Impact
- Affected specs: 无（全新项目）
- Affected code: 无（全新项目，当前仓库仅有 README.md）

## ADDED Requirements

### Requirement: B站视频下载
系统 SHALL 提供通过B站视频 URL 或 BV 号下载视频的功能。

#### Scenario: 输入有效URL下载成功
- **WHEN** 用户输入有效的B站视频 URL（如 https://www.bilibili.com/video/BVxxxxxx）
- **THEN** 系统使用 yt-dlp 下载视频，返回本地视频文件路径

#### Scenario: 输入无效URL
- **WHEN** 用户输入无效的B站视频 URL
- **THEN** 系统返回错误提示，要求用户检查链接有效性

### Requirement: 音频提取
系统 SHALL 从下载的视频中提取音频为 WAV 格式（16kHz，单声道），以满足百炼 ASR 输入要求。

#### Scenario: 提取音频成功
- **WHEN** 视频文件下载完成
- **THEN** 系统使用 ffmpeg 提取音频为 WAV 格式（16kHz，单声道）

### Requirement: 语音识别
系统 SHALL 使用阿里云百炼 Paraformer API 对提取的音频进行语音识别，返回带时间戳的文本结果。

#### Scenario: 短音频识别
- **WHEN** 音频时长 ≤ 5 分钟
- **THEN** 系统直接调用百炼 Paraformer 同步识别接口，返回带时间戳的识别结果

#### Scenario: 长音频识别
- **WHEN** 音频时长 > 5 分钟
- **THEN** 系统将音频分段（每段 ≤ 5 分钟），逐段调用识别接口，合并结果

#### Scenario: API 调用失败
- **WHEN** 百炼 API 调用失败
- **THEN** 系统自动重试 3 次（指数退避），仍失败则标记任务失败并通知用户

### Requirement: 多语言翻译
系统 SHALL 使用阿里云百炼 Qwen 大模型将识别文本翻译为目标语言，支持用户选择源语言和目标语言。

#### Scenario: 翻译字幕成功
- **WHEN** 语音识别完成，用户已选择源语言和目标语言
- **THEN** 系统调用 Qwen 将识别文本分批翻译（每批最多 50 行），保持时间戳对应关系

#### Scenario: 翻译质量不佳
- **WHEN** 用户对翻译结果不满意
- **THEN** 用户可在字幕编辑器中手动修正翻译文本

### Requirement: SRT 字幕生成
系统 SHALL 将识别结果和翻译结果组装为 SRT 格式字幕文件，支持双语字幕（源语言 + 目标语言）。

#### Scenario: 生成 SRT 文件
- **WHEN** 翻译完成
- **THEN** 系统生成包含源文本和翻译文本的 SRT 字幕文件

### Requirement: 硬字幕烧录
系统 SHALL 使用 ffmpeg 将 SRT 字幕烧录到视频中，生成带硬字幕的视频文件。

#### Scenario: 烧录硬字幕成功
- **WHEN** SRT 字幕文件生成完成
- **THEN** 系统使用 ffmpeg 将字幕烧录到视频中，字体大小、颜色、位置可配置

### Requirement: 实时进度推送
系统 SHALL 通过 WebSocket 实时推送处理进度到前端。

#### Scenario: 进度更新
- **WHEN** 流水线各阶段执行中
- **THEN** 系统通过 WebSocket 推送当前阶段（downloading/extracting_audio/recognizing/translating/generating_subtitle/burning_subtitle）、进度百分比（0.0~1.0）和描述信息

### Requirement: Web 前端界面
系统 SHALL 提供 Web 前端界面，包含视频输入、进度展示、字幕编辑、视频预览、文件下载功能。

#### Scenario: 用户提交处理任务
- **WHEN** 用户在输入区填写B站 URL、选择源语言和目标语言、点击开始处理
- **THEN** 系统创建处理任务，前端通过 WebSocket 实时显示各步骤进度

#### Scenario: 用户预览和编辑字幕
- **WHEN** 任务完成后
- **THEN** 前端展示字幕列表（源文本 + 翻译文本），用户可编辑翻译文本并保存

#### Scenario: 用户下载结果
- **WHEN** 用户点击下载按钮
- **THEN** 用户可下载 SRT 字幕文件或带硬字幕的视频文件

### Requirement: 字幕编辑与重新烧录
系统 SHALL 支持用户编辑字幕后重新烧录视频。

#### Scenario: 编辑后重新烧录
- **WHEN** 用户编辑字幕内容并保存后，点击重新烧录
- **THEN** 系统使用编辑后的字幕重新烧录视频

### Requirement: 错误处理
系统 SHALL 对各类异常场景提供明确的错误处理。

#### Scenario: ffmpeg 未安装
- **WHEN** 系统检测到 ffmpeg 未安装
- **THEN** 返回错误信息并给出安装指引

#### Scenario: 磁盘空间不足
- **WHEN** 处理前检测到磁盘空间不足
- **THEN** 提前报错，不开始处理

### Requirement: 临时文件管理
系统 SHALL 管理处理过程中产生的临时文件。

#### Scenario: 文件存储与清理
- **WHEN** 任务执行中
- **THEN** 中间文件存放在 `temp/{task_id}/` 目录，任务完成后保留 24 小时，定期清理过期文件

## MODIFIED Requirements
无

## REMOVED Requirements
无
