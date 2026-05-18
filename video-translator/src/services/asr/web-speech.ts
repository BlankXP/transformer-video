import { ASRProvider } from './asr-provider';
import { ASROptions, ASRResult } from '../../shared/types';

export class WebSpeechASR implements ASRProvider {
  name = 'web-speech';
  private recognition: any = null;

  async recognize(_audioBlob: Blob, _options?: ASROptions): Promise<ASRResult> {
    return Promise.reject(
      new Error('Web Speech API 不支持从音频文件识别，请使用实时识别模式')
    );
  }

  startRealtimeRecognition(
    options?: ASROptions,
    onResult?: (text: string, isFinal: boolean) => void
  ): void {
    const SpeechRecognitionCtor =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

    if (!SpeechRecognitionCtor) {
      throw new Error('Web Speech API is not available');
    }

    this.recognition = new SpeechRecognitionCtor();
    this.recognition.continuous = true;
    this.recognition.interimResults = true;
    this.recognition.lang = options?.language || '';

    this.recognition.onresult = (event: any) => {
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        const transcript = result[0].transcript;
        if (onResult) {
          onResult(transcript, result.isFinal);
        }
      }
    };

    this.recognition.onerror = (event: any) => {
      console.error('Web Speech recognition error:', event.error);
    };

    this.recognition.start();
  }

  stopRealtimeRecognition(): void {
    if (this.recognition) {
      this.recognition.stop();
      this.recognition = null;
    }
  }

  isAvailable(): boolean {
    return (
      typeof (window as any).webkitSpeechRecognition !== 'undefined' ||
      typeof (window as any).SpeechRecognition !== 'undefined'
    );
  }
}
