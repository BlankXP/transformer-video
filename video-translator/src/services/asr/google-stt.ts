import { ASRProvider } from './asr-provider';
import { ASROptions, ASRResult, WordTimestamp } from '../../shared/types';

export class GoogleSTT implements ASRProvider {
  name = 'google-stt';
  private apiKey: string;

  constructor(apiKey: string) {
    this.apiKey = apiKey;
  }

  async recognize(audioBlob: Blob, options?: ASROptions): Promise<ASRResult> {
    const base64Audio = await this.blobToBase64(audioBlob);

    const encoding = audioBlob.type.includes('webm') ? 'WEBM_OPUS' : 'LINEAR16';
    const languageCode = options?.language || 'auto';

    const response = await fetch(
      `https://speech.googleapis.com/v1/speech:recognize?key=${this.apiKey}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          config: {
            encoding,
            sampleRateHertz: 48000,
            languageCode,
            enableWordTimeOffsets: true,
          },
          audio: {
            content: base64Audio,
          },
        }),
      }
    );

    if (!response.ok) {
      throw new Error(`Google STT API error: ${response.status} ${response.statusText}`);
    }

    const data = await response.json();
    const result = data.results?.[0];
    const alternative = result?.alternatives?.[0];

    if (!alternative) {
      return {
        text: '',
        language: languageCode,
        confidence: 0,
      };
    }

    const words: WordTimestamp[] | undefined = alternative.words
      ? alternative.words.map((w: any) => ({
          word: w.word,
          startTime: this.parseGoogleDuration(w.startTime),
          endTime: this.parseGoogleDuration(w.endTime),
        }))
      : undefined;

    return {
      text: alternative.transcript || '',
      language: result.languageCode || languageCode,
      confidence: alternative.confidence || 0,
      words,
    };
  }

  private parseGoogleDuration(duration: string): number {
    if (!duration) return 0;
    const match = duration.match(/(\d+)s(\d+)ns/);
    if (match) {
      return parseFloat(match[1]) * 1000 + parseFloat(match[2]) / 1000000;
    }
    const seconds = parseFloat(duration.replace('s', ''));
    return seconds * 1000;
  }

  private blobToBase64(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onloadend = () => {
        const base64 = (reader.result as string).split(',')[1];
        resolve(base64);
      };
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });
  }

  isAvailable(): boolean {
    return !!this.apiKey;
  }

  setApiKey(key: string): void {
    this.apiKey = key;
  }
}
