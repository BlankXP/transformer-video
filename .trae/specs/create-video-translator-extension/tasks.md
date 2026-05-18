# Tasks

- [x] Task 1: 项目脚手架搭建
  - [x] SubTask 1.1: 初始化项目结构，创建 package.json、tsconfig.json、webpack.config.js
  - [x] SubTask 1.2: 创建 manifest.json（Manifest V3），声明权限和入口
  - [x] SubTask 1.3: 创建共享类型定义文件 src/shared/types.ts
  - [x] SubTask 1.4: 创建常量定义文件 src/shared/constants.ts
  - [x] SubTask 1.5: 创建消息通信封装 src/shared/message.ts
  - [x] SubTask 1.6: 创建存储封装 src/shared/storage.ts
  - [x] SubTask 1.7: 验证构建流程可正常运行

- [x] Task 2: 字幕检测模块
  - [x] SubTask 2.1: 实现 HTML5 `<track>` 元素检测与 WebVTT/SRT 解析
  - [x] SubTask 2.2: 实现 YouTube 平台字幕提取
  - [x] SubTask 2.3: 实现 Bilibili 平台字幕提取
  - [x] SubTask 2.4: 实现 DOM 字幕监听（MutationObserver）
  - [x] SubTask 2.5: 实现字幕检测调度器（按优先级依次尝试）

- [x] Task 3: 音频捕获与 ASR 服务
  - [x] SubTask 3.1: 实现音频捕获模块（Web Audio API + MediaRecorder）
  - [x] SubTask 3.2: 实现 ASR 服务抽象接口 src/services/asr/asr-provider.ts
  - [x] SubTask 3.3: 实现 OpenAI Whisper ASR 提供商
  - [x] SubTask 3.4: 实现 Google Speech-to-Text ASR 提供商
  - [x] SubTask 3.5: 实现 Web Speech API 回退方案
  - [x] SubTask 3.6: 实现 ASR 调度器（优先级切换 + 错误回退）

- [x] Task 4: 翻译服务
  - [x] SubTask 4.1: 实现翻译服务抽象接口 src/services/translate/translate-provider.ts
  - [x] SubTask 4.2: 实现 Google Cloud Translation 提供商
  - [x] SubTask 4.3: 实现 DeepL 翻译提供商
  - [x] SubTask 4.4: 实现 OpenAI GPT 翻译提供商
  - [x] SubTask 4.5: 实现翻译调度器（主备切换 + 速率控制）
  - [x] SubTask 4.6: 实现翻译缓存（chrome.storage.local + 7天过期）
  - [x] SubTask 4.7: 实现批量翻译优化（短句合并）

- [x] Task 5: 字幕渲染模块
  - [x] SubTask 5.1: 实现字幕叠加层创建（Shadow DOM 隔离）
  - [x] SubTask 5.2: 实现字幕同步机制（timeupdate 事件 + seek 处理）
  - [x] SubTask 5.3: 实现字幕样式自定义（字体大小、颜色、背景、位置）

- [x] Task 6: Content Script 整合
  - [x] SubTask 6.1: 实现视频元素检测与注册
  - [x] SubTask 6.2: 整合字幕检测 → 翻译 → 渲染流程（有字幕路径）
  - [x] SubTask 6.3: 整合音频捕获 → ASR → 翻译 → 渲染流程（无字幕路径）
  - [x] SubTask 6.4: 实现与 Background 的消息通信

- [x] Task 7: Background Service Worker
  - [x] SubTask 7.1: 实现消息路由与分发
  - [x] SubTask 7.2: 实现 ASR API 调用代理
  - [x] SubTask 7.3: 实现翻译 API 调用代理
  - [x] SubTask 7.4: 实现错误处理与重试逻辑

- [x] Task 8: Popup 控制面板
  - [x] SubTask 8.1: 创建 popup.html 结构
  - [x] SubTask 8.2: 实现视频检测状态显示
  - [x] SubTask 8.3: 实现翻译开关控制
  - [x] SubTask 8.4: 实现语言选择（源语言 + 目标语言）
  - [x] SubTask 8.5: 实现字幕样式快捷调整
  - [x] SubTask 8.6: 实现错误状态提示

- [x] Task 9: Options 设置页面
  - [x] SubTask 9.1: 创建 options.html 结构
  - [x] SubTask 9.2: 实现 API Key 配置（ASR + 翻译服务商选择与 Key 输入）
  - [x] SubTask 9.3: 实现语言偏好设置
  - [x] SubTask 9.4: 实现字幕样式详细设置
  - [x] SubTask 9.5: 实现高级设置（分段时长、缓存开关、自动切换）
  - [x] SubTask 9.6: 实现设置保存与加载

- [x] Task 10: 端到端集成测试
  - [x] SubTask 10.1: 验证有字幕路径（YouTube 视频翻译）
  - [x] SubTask 10.2: 验证无字幕路径（音频捕获 + ASR + 翻译）
  - [x] SubTask 10.3: 验证 Popup 控制流程
  - [x] SubTask 10.4: 验证 Options 配置持久化
  - [x] SubTask 10.5: 验证错误处理与回退机制

# Task Dependencies
- [Task 2] depends on [Task 1]
- [Task 3] depends on [Task 1]
- [Task 4] depends on [Task 1]
- [Task 5] depends on [Task 1]
- [Task 6] depends on [Task 2, Task 3, Task 4, Task 5]
- [Task 7] depends on [Task 3, Task 4]
- [Task 8] depends on [Task 1, Task 6]
- [Task 9] depends on [Task 1]
- [Task 10] depends on [Task 6, Task 7, Task 8, Task 9]
