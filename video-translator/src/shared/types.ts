export interface SubtitleCue {
  id: string;
  startTime: number;
  endTime: number;
  text: string;
  translatedText?: string;
}

export interface SubtitleTrack {
  language: string;
  label: string;
  cues: SubtitleCue[];
  source: 'track' | 'platform' | 'dom';
}

export interface SubtitleStyle {
  fontSize: number;
  fontColor: string;
  bgColor: string;
  position: 'bottom' | 'top';
  opacity: number;
}

export interface ASROptions {
  language?: string;
  sampleRate?: number;
}

export interface ASRResult {
  text: string;
  language: string;
  confidence: number;
  words?: WordTimestamp[];
}

export interface WordTimestamp {
  word: string;
  startTime: number;
  endTime: number;
}

export interface TranslateOptions {
  sourceLang?: string;
  targetLang: string;
}

export interface TranslateResult {
  translatedText: string;
  detectedSourceLang?: string;
  confidence?: number;
}

export interface Settings {
  asrProvider: string;
  asrApiKey: string;
  translateProvider: string;
  translateApiKey: string;
  defaultSourceLang: string;
  defaultTargetLang: string;
  autoDetectSource: boolean;
  subtitleStyle: SubtitleStyle;
  segmentDuration: number;
  cacheEnabled: boolean;
  autoSwitchProvider: boolean;
}

export type Message =
  | { type: 'VIDEO_DETECTED'; payload: { videoId: string; hasSubtitle: boolean } }
  | { type: 'AUDIO_SEGMENT'; payload: { videoId: string; audioBase64: string; timestamp: number; index: number } }
  | { type: 'TRANSLATE_REQUEST'; payload: { text: string; sourceLang?: string; targetLang: string } }
  | { type: 'TRANSLATE_RESULT'; payload: { videoId: string; cues: SubtitleCue[] } }
  | { type: 'ASR_REQUEST'; payload: { audioBase64: string; language?: string } }
  | { type: 'ASR_RESULT'; payload: { videoId: string; text: string; language: string; timestamp: number; index: number } }
  | { type: 'ERROR'; payload: { source: string; message: string } }
  | { type: 'TOGGLE_TRANSLATION'; payload: { enabled: boolean } }
  | { type: 'UPDATE_SETTINGS'; payload: Partial<Settings> }
  | { type: 'GET_STATUS'; payload: {} }
  | { type: 'STATUS_RESPONSE'; payload: { videoDetected: boolean; hasSubtitle: boolean; subtitleSource?: string; translating: boolean } }
