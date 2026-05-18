export const DEFAULT_SETTINGS = {
  asrProvider: 'whisper',
  asrApiKey: '',
  translateProvider: 'google',
  translateApiKey: '',
  defaultSourceLang: '',
  defaultTargetLang: 'zh-CN',
  autoDetectSource: true,
  subtitleStyle: {
    fontSize: 18,
    fontColor: '#FFFFFF',
    bgColor: 'rgba(0,0,0,0.7)',
    position: 'bottom' as const,
    opacity: 0.9,
  },
  segmentDuration: 8,
  cacheEnabled: true,
  autoSwitchProvider: true,
};

export const SUPPORTED_LANGUAGES = [
  { code: 'zh-CN', label: '中文（简体）' },
  { code: 'zh-TW', label: '中文（繁体）' },
  { code: 'en', label: 'English' },
  { code: 'ja', label: '日本語' },
  { code: 'ko', label: '한국어' },
  { code: 'fr', label: 'Français' },
  { code: 'de', label: 'Deutsch' },
  { code: 'es', label: 'Español' },
  { code: 'pt', label: 'Português' },
  { code: 'ru', label: 'Русский' },
  { code: 'ar', label: 'العربية' },
  { code: 'hi', label: 'हिन्दी' },
  { code: 'th', label: 'ไทย' },
  { code: 'vi', label: 'Tiếng Việt' },
  { code: 'id', label: 'Bahasa Indonesia' },
  { code: 'it', label: 'Italiano' },
  { code: 'nl', label: 'Nederlands' },
  { code: 'pl', label: 'Polski' },
  { code: 'tr', label: 'Türkçe' },
  { code: 'uk', label: 'Українська' },
];

export const ASR_PROVIDERS = [
  { id: 'whisper', label: 'OpenAI Whisper', requiresKey: true },
  { id: 'google-stt', label: 'Google Speech-to-Text', requiresKey: true },
  { id: 'web-speech', label: '浏览器 Web Speech API', requiresKey: false },
];

export const TRANSLATE_PROVIDERS = [
  { id: 'google', label: 'Google Cloud Translation', requiresKey: true },
  { id: 'deepl', label: 'DeepL', requiresKey: true },
  { id: 'openai', label: 'OpenAI GPT', requiresKey: true },
];

export const CACHE_EXPIRY_DAYS = 7;
export const MAX_RETRY_COUNT = 1;
export const RETRY_DELAY_MS = 1000;
