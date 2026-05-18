import { TranslateProvider } from './translate-provider';
import { TranslateOptions, TranslateResult } from '../../shared/types';

export class DeepLTranslate implements TranslateProvider {
  name = 'deepl';
  private apiKey: string;

  constructor(apiKey: string) {
    this.apiKey = apiKey;
  }

  private get isFreeApi(): boolean {
    return this.apiKey.endsWith(':fx');
  }

  private get baseUrl(): string {
    return this.isFreeApi
      ? 'https://api-free.deepl.com/v2'
      : 'https://api.deepl.com/v2';
  }

  async translate(text: string, options: TranslateOptions): Promise<TranslateResult> {
    const url = `${this.baseUrl}/translate`;

    const body: Record<string, unknown> = {
      text: [text],
      target_lang: options.targetLang,
    };

    if (options.sourceLang) {
      body.source_lang = options.sourceLang;
    }

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `DeepL-Auth-Key ${this.apiKey}`,
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      throw new Error(`DeepL API error: ${response.status}`);
    }

    const data = await response.json();
    const translation = data.translations[0];

    return {
      translatedText: translation.text,
      detectedSourceLang: translation.detected_source_language,
    };
  }

  async detectLanguage(text: string): Promise<string> {
    const url = `${this.baseUrl}/detect`;

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `DeepL-Auth-Key ${this.apiKey}`,
      },
      body: JSON.stringify({ text: [text] }),
    });

    if (!response.ok) {
      throw new Error(`DeepL Detect API error: ${response.status}`);
    }

    const data = await response.json();
    return data[0].language;
  }

  isAvailable(): boolean {
    return !!this.apiKey;
  }

  setApiKey(key: string): void {
    this.apiKey = key;
  }
}
