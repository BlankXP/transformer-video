import { onMessage, sendMessageToTab } from '../shared/message';
import { getSettings, saveSettings } from '../shared/storage';
import { Settings, Message } from '../shared/types';
import { MAX_RETRY_COUNT, RETRY_DELAY_MS } from '../shared/constants';
import { ASRDispatcher } from '../services/asr/asr-provider';
import { WhisperASR } from '../services/asr/whisper';
import { GoogleSTT } from '../services/asr/google-stt';
import { WebSpeechASR } from '../services/asr/web-speech';
import { TranslateDispatcher } from '../services/translate/translate-provider';
import { GoogleTranslate } from '../services/translate/google-translate';
import { DeepLTranslate } from '../services/translate/deepl';
import { OpenAITranslate } from '../services/translate/openai';

let asrDispatcher: ASRDispatcher;
let translateDispatcher: TranslateDispatcher;
let settings: Settings;

async function initProviders(): Promise<void> {
  settings = await getSettings();
  asrDispatcher = new ASRDispatcher();
  translateDispatcher = new TranslateDispatcher();

  if (settings.asrApiKey) {
    asrDispatcher.registerProvider(new WhisperASR(settings.asrApiKey));
    asrDispatcher.registerProvider(new GoogleSTT(settings.asrApiKey));
  }
  asrDispatcher.registerProvider(new WebSpeechASR());

  if (settings.translateApiKey) {
    translateDispatcher.registerProvider(new GoogleTranslate(settings.translateApiKey));
    translateDispatcher.registerProvider(new DeepLTranslate(settings.translateApiKey));
    translateDispatcher.registerProvider(new OpenAITranslate(settings.translateApiKey));
  }
}

async function retryWithBackoff<T>(
  fn: () => Promise<T>,
  maxRetries: number = MAX_RETRY_COUNT,
  delayMs: number = RETRY_DELAY_MS
): Promise<T> {
  let lastError: Error | null = null;
  for (let i = 0; i <= maxRetries; i++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error as Error;
      if (i < maxRetries) {
        await new Promise<void>((resolve) => setTimeout(resolve, delayMs * Math.pow(2, i)));
      }
    }
  }
  throw lastError;
}

function base64ToBlob(base64: string, mimeType: string): Blob {
  const binaryString = atob(base64);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return new Blob([bytes], { type: mimeType });
}

onMessage(async (message: Message, sender: chrome.runtime.MessageSender) => {
  switch (message.type) {
    case 'VIDEO_DETECTED': {
      console.log('Video detected:', message.payload.videoId, 'hasSubtitle:', message.payload.hasSubtitle);
      return { acknowledged: true };
    }

    case 'AUDIO_SEGMENT': {
      try {
        const audioBlob = base64ToBlob(message.payload.audioBase64, 'audio/webm');
        const asrLang = settings.autoDetectSource ? undefined : settings.defaultSourceLang;
        const result = await retryWithBackoff(() =>
          asrDispatcher.recognize(audioBlob, { language: asrLang })
        );
        return {
          text: result.text,
          language: result.language,
        };
      } catch (error) {
        return {
          type: 'ERROR',
          payload: { source: 'AUDIO_SEGMENT', message: (error as Error).message },
        };
      }
    }

    case 'ASR_REQUEST': {
      try {
        const audioBlob = base64ToBlob(message.payload.audioBase64, 'audio/webm');
        const result = await retryWithBackoff(() =>
          asrDispatcher.recognize(audioBlob, { language: message.payload.language })
        );
        return {
          text: result.text,
          language: result.language,
          confidence: result.confidence,
        };
      } catch (error) {
        return {
          type: 'ERROR',
          payload: { source: 'ASR_REQUEST', message: (error as Error).message },
        };
      }
    }

    case 'TRANSLATE_REQUEST': {
      try {
        const result = await retryWithBackoff(() =>
          translateDispatcher.translateWithCache(message.payload.text, {
            sourceLang: message.payload.sourceLang,
            targetLang: message.payload.targetLang,
          })
        );
        return {
          translatedText: result.translatedText,
          detectedSourceLang: result.detectedSourceLang,
          confidence: result.confidence,
        };
      } catch (error) {
        return {
          type: 'ERROR',
          payload: { source: 'TRANSLATE_REQUEST', message: (error as Error).message },
        };
      }
    }

    case 'TOGGLE_TRANSLATION': {
      try {
        const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
        if (tab?.id != null) {
          await sendMessageToTab(tab.id, {
            type: 'TOGGLE_TRANSLATION',
            payload: { enabled: message.payload.enabled },
          });
        }
        return { acknowledged: true };
      } catch (error) {
        return {
          type: 'ERROR',
          payload: { source: 'TOGGLE_TRANSLATION', message: (error as Error).message },
        };
      }
    }

    case 'UPDATE_SETTINGS': {
      try {
        settings = {
          ...settings,
          ...message.payload,
          subtitleStyle: message.payload.subtitleStyle
            ? { ...settings.subtitleStyle, ...message.payload.subtitleStyle }
            : settings.subtitleStyle,
        };
        await saveSettings(settings);
        await initProviders();
        return { acknowledged: true };
      } catch (error) {
        return {
          type: 'ERROR',
          payload: { source: 'UPDATE_SETTINGS', message: (error as Error).message },
        };
      }
    }

    case 'GET_STATUS': {
      const asrProvider = asrDispatcher?.getCurrentProvider();
      const translateProvider = translateDispatcher?.getCurrentProvider();
      return {
        asrProvider: asrProvider ? { name: asrProvider.name, available: asrProvider.isAvailable() } : null,
        translateProvider: translateProvider ? { name: translateProvider.name, available: translateProvider.isAvailable() } : null,
      };
    }

    default:
      return {};
  }
});

initProviders();
