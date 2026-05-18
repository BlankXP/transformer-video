import { ASROptions, ASRResult } from '../../shared/types';

export interface ASRProvider {
  name: string;
  recognize(audioBlob: Blob, options?: ASROptions): Promise<ASRResult>;
  isAvailable(): boolean;
}

export class ASRDispatcher {
  private providers: ASRProvider[] = [];
  private currentProviderIndex: number = 0;

  registerProvider(provider: ASRProvider): void {
    this.providers.push(provider);
  }

  async recognize(audioBlob: Blob, options?: ASROptions): Promise<ASRResult> {
    if (this.providers.length === 0) {
      throw new Error('No ASR providers registered');
    }

    let startIndex = this.currentProviderIndex;
    let attempt = 0;

    for (let i = 0; i < this.providers.length; i++) {
      const providerIndex = (startIndex + i) % this.providers.length;
      const provider = this.providers[providerIndex];

      try {
        const result = await provider.recognize(audioBlob, options);
        this.currentProviderIndex = providerIndex;
        return result;
      } catch {
        attempt++;
        if (i < this.providers.length - 1) {
          await new Promise<void>(resolve => setTimeout(resolve, 1000 * attempt));
        }
      }
    }

    throw new Error('All ASR providers failed');
  }

  getCurrentProvider(): ASRProvider | null {
    if (this.providers.length === 0) {
      return null;
    }
    return this.providers[this.currentProviderIndex] || null;
  }
}
