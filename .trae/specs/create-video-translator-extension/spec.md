# Video Translator Chrome Extension Spec

## Why
用户在浏览网页视频时，经常遇到语言障碍，需要一种便捷的方式将视频中的语音或字幕翻译为自己理解的语言。当前市面上的翻译工具大多仅支持特定平台，缺少一个通用的、可在任意网页视频上使用的翻译插件。

## What Changes
- 创建一个 Chrome Extension（Manifest V3），能够翻译任意网页中的视频内容
- 实现智能混合翻译路径：优先检测并翻译现有字幕，无字幕时回退到音频捕获 + ASR + 翻译
- 支持 Popup 控制面板和 Options 设置页面
- 支持多种云端 ASR 和翻译服务商，带浏览器内置方案回退
- 在视频上叠加翻译字幕，支持样式自定义

## Impact
- Affected code: 全新项目，无现有代码受影响
- 依赖外部服务: OpenAI Whisper API / Google Speech-to-Text / Google Cloud Translation / DeepL API

## ADDED Requirements

### Requirement: 视频检测与字幕检测
系统 SHALL 在用户访问任意网页时，自动检测页面中的 `<video>` 元素，并按优先级依次检测现有字幕来源：
1. HTML5 `<track>` 元素
2. 平台特定字幕 API（YouTube、Bilibili）
3. 页面 DOM 字幕元素（MutationObserver）

#### Scenario: 检测到视频且有内置字幕
- **WHEN** 用户访问包含视频的网页，且视频有可用字幕轨道
- **THEN** 插件在 Popup 中显示"已检测到视频"及字幕来源，用户可直接开启翻译

#### Scenario: 检测到视频但无字幕
- **WHEN** 用户访问包含视频的网页，但视频无可用的字幕轨道
- **THEN** 插件在 Popup 中提示将使用音频识别模式

#### Scenario: 未检测到视频
- **WHEN** 用户访问的网页不包含 `<video>` 元素
- **THEN** Popup 显示"未找到视频"

### Requirement: 音频捕获与 ASR
系统 SHALL 在无现有字幕时，从 `<video>` 元素捕获音频并通过 ASR 服务转为文字。

#### Scenario: 云端 ASR 识别
- **WHEN** 用户开启翻译且无现有字幕，且已配置云端 ASR API Key
- **THEN** 系统使用 Web Audio API 捕获音频，按 5-10 秒分段发送给云端 ASR，返回识别文本及时间戳

#### Scenario: 浏览器 Web Speech API 回退
- **WHEN** 用户未配置任何云端 ASR API Key
- **THEN** 系统回退到浏览器 Web Speech API 进行实时识别，并在 Popup 中提示此为回退模式

#### Scenario: 音频捕获受 CORS 限制
- **WHEN** 跨域视频的音频捕获因 CORS 策略失败
- **THEN** 系统提示用户视频可能受 CORS 限制，建议刷新页面或使用有字幕的视频

### Requirement: 翻译服务
系统 SHALL 支持多种云端翻译服务商，将识别出的文本翻译为目标语言。

#### Scenario: 正常翻译
- **WHEN** ASR 返回识别文本或字幕检测提取到原文
- **THEN** 系统调用用户配置的翻译服务，将文本翻译为目标语言

#### Scenario: 翻译服务自动切换
- **WHEN** 首选翻译服务调用失败
- **THEN** 系统自动切换到备选翻译服务重试

#### Scenario: 语言自动检测
- **WHEN** 用户选择"自动检测"源语言
- **THEN** 系统优先使用 ASR 返回的语言检测结果，其次使用翻译 API 的语言检测，最后使用 `<track>` 的 `srclang` 属性

### Requirement: 字幕渲染
系统 SHALL 在视频上叠加翻译后的字幕，并支持样式自定义。

#### Scenario: 字幕同步显示
- **WHEN** 视频播放到某个时间点
- **THEN** 系统根据 `video.currentTime` 匹配对应字幕的 `startTime`/`endTime`，显示翻译后的字幕文本

#### Scenario: 视频跳转
- **WHEN** 用户拖动视频进度条跳转
- **THEN** 系统清空当前字幕缓冲，根据新时间点重新定位字幕

#### Scenario: 字幕样式自定义
- **WHEN** 用户在 Popup 或 Options 中调整字幕样式
- **THEN** 字幕叠加层实时更新字体大小、颜色、背景色、位置等属性

### Requirement: Popup 控制面板
系统 SHALL 提供 Popup 界面，允许用户控制翻译功能。

#### Scenario: 开启/关闭翻译
- **WHEN** 用户点击 Popup 中的翻译开关
- **THEN** 系统开始或停止翻译流程，字幕叠加层相应显示或隐藏

#### Scenario: 语言选择
- **WHEN** 用户在 Popup 中选择源语言和目标语言
- **THEN** 系统使用选择的语言对进行后续翻译

### Requirement: Options 设置页面
系统 SHALL 提供 Options 页面，允许用户配置 API Key 和偏好设置。

#### Scenario: 配置 API Key
- **WHEN** 用户在 Options 页面输入 ASR 或翻译服务的 API Key
- **THEN** 系统保存 API Key 到 `chrome.storage.local`，并验证其有效性

#### Scenario: 设置默认目标语言
- **WHEN** 用户在 Options 页面设置默认目标语言
- **THEN** 后续翻译默认使用该目标语言，Popup 中仍可临时覆盖

### Requirement: 错误处理与容错
系统 SHALL 对各类错误场景提供优雅的处理。

#### Scenario: API Key 未配置
- **WHEN** 用户尝试开启翻译但未配置必要的 API Key
- **THEN** Popup 显示配置引导，点击可跳转 Options 页面

#### Scenario: API 调用失败
- **WHEN** ASR 或翻译 API 调用失败
- **THEN** 系统自动重试 1 次（指数退避），仍失败则切换备选服务，全部失败则在 Popup 显示错误提示

#### Scenario: 网络断开
- **WHEN** 网络连接中断
- **THEN** 系统暂停处理，缓存待翻译文本，网络恢复后自动继续

### Requirement: 翻译缓存
系统 SHALL 对翻译结果进行缓存以减少 API 调用。

#### Scenario: 缓存命中
- **WHEN** 相同原文 + 源语言 + 目标语言 + 翻译服务的翻译请求
- **THEN** 直接返回缓存结果，不调用翻译 API

#### Scenario: 缓存过期
- **WHEN** 缓存条目超过 7 天
- **THEN** 下次请求时重新调用翻译 API 并更新缓存
