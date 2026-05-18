import { TranslateProvider } from './translate-provider';
import { TranslateOptions, TranslateResult } from '../../shared/types';

export class GoogleTranslate implements TranslateProvider {
  name = 'google';
  private apiKey: string;

  constructor(apiKey: string) {
    this.apiKey = apiKey;
  }

  async translate(text: string, options: TranslateOptions): Promise<TranslateResult> {
    const url = `https://translation.googleapis.com/language/translate/v2?key=${this.apiKey}`;

    const body: Record<string, unknown> = {
      target: options.targetLang,
      format: 'text',
    };

    if (text.includes('\n')) {
      body.q = text.split('\n');
    } else {
      body.q = text;
    }

    if (options.sourceLang) {
      body.source = options.sourceLang;
    }

    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      throw new Error(`Google Translate API error: ${response.status}`);
    }

    const data = await response.json();
    const translations = data.data.translations;

    if (Array.isArray(body.q)) {
      const translatedText = translations.map((t: { translatedText: string }) => t.translatedText).join('\n');
      return {
        translatedText,
        detectedSourceLang: translations[0]?.detectedSourceLanguage,
      };
    }

    return {
      translatedText: translations[0].translatedText,
      detectedSourceLang: translations[0]?.detectedSourceLanguage,
    };
  }

  async detectLanguage(text: string): Promise<string> {
    const url = `https://translation.googleapis.com/language/translate/v2/detect?key=${this.apiKey}`;

    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ q: text }),
    });

    if (!response.ok) {
      throw new Error(`Google Detect API error: ${response.status}`);
    }

    const data = await response.json();
    return data.data.detections[0][0].language;
  }

  isAvailable(): boolean {
    return !!this.apiKey;
  }

  setApiKey(key: string): void {
    this.apiKey = key;
  }
}
