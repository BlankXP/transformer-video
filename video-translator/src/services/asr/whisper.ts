import { ASRProvider } from './asr-provider';
import { ASROptions, ASRResult, WordTimestamp } from '../../shared/types';

export class WhisperASR implements ASRProvider {
  name = 'whisper';
  private apiKey: string;

  constructor(apiKey: string) {
    this.apiKey = apiKey;
  }

  async recognize(audioBlob: Blob, options?: ASROptions): Promise<ASRResult> {
    const formData = new FormData();
    formData.append('file', audioBlob, 'audio.webm');
    formData.append('model', 'whisper-1');
    formData.append('response_format', 'verbose_json');

    if (options?.language) {
      formData.append('language', options.language);
    }

    const response = await fetch('https://api.openai.com/v1/audio/transcriptions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${this.apiKey}`,
      },
      body: formData,
    });

    if (!response.ok) {
      throw new Error(`Whisper API error: ${response.status} ${response.statusText}`);
    }

    const data = await response.json();

    const words: WordTimestamp[] | undefined = data.segments
      ? data.segments.flatMap((segment: any) =>
          segment.words
            ? segment.words.map((w: any) => ({
                word: w.word,
                startTime: w.start * 1000,
                endTime: w.end * 1000,
              }))
            : []
        )
      : undefined;

    return {
      text: data.text,
      language: data.language || options?.language || '',
      confidence: 1,
      words,
    };
  }

  isAvailable(): boolean {
    return !!this.apiKey;
  }

  setApiKey(key: string): void {
    this.apiKey = key;
  }
}
