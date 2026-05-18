import { TranslateOptions, TranslateResult } from '../../shared/types';
import { getCachedTranslation, setCachedTranslation } from '../../shared/storage';

export interface TranslateProvider {
  name: string;
  translate(text: string, options: TranslateOptions): Promise<TranslateResult>;
  detectLanguage(text: string): Promise<string>;
  isAvailable(): boolean;
}

export class TranslateDispatcher {
  private providers: TranslateProvider[] = [];
  private currentProviderIndex: number = 0;
  private requestQueue: Array<() => Promise<void>> = [];
  private isProcessing: boolean = false;
  private qpsLimit: number = 10;

  registerProvider(provider: TranslateProvider): void {
    this.providers.push(provider);
  }

  async translate(text: string, options: TranslateOptions): Promise<TranslateResult> {
    if (this.providers.length === 0) {
      throw new Error('No translate providers registered');
    }

    const startIndex = this.currentProviderIndex;
    let lastError: Error | null = null;

    for (let i = 0; i < this.providers.length; i++) {
      const index = (startIndex + i) % this.providers.length;
      const provider = this.providers[index];
      if (!provider.isAvailable()) continue;

      try {
        const result = await this.enqueue(() => provider.translate(text, options));
        this.currentProviderIndex = index;
        return result;
      } catch (error) {
        lastError = error as Error;
      }
    }

    throw new Error(lastError?.message ?? 'All translate providers failed');
  }

  async detectLanguage(text: string): Promise<string> {
    const provider = this.getCurrentProvider();
    if (!provider || !provider.isAvailable()) {
      throw new Error('No available translate provider');
    }
    return this.enqueue(() => provider.detectLanguage(text));
  }

  getCurrentProvider(): TranslateProvider | null {
    if (this.providers.length === 0) return null;
    return this.providers[this.currentProviderIndex] ?? null;
  }

  async translateWithCache(text: string, options: TranslateOptions): Promise<TranslateResult> {
    const providerName = this.getCurrentProvider()?.name ?? 'unknown';
    const cacheKey = `${text}_${options.sourceLang ?? 'auto'}_${options.targetLang}_${providerName}`;

    const cached = await getCachedTranslation(cacheKey);
    if (cached !== null) {
      return { translatedText: cached };
    }

    const result = await this.translate(text, options);
    await setCachedTranslation(cacheKey, result.translatedText);
    return result;
  }

  async batchTranslate(
    items: Array<{ id: string; text: string }>,
    options: TranslateOptions
  ): Promise<Array<{ id: string; result: TranslateResult }>> {
    if (items.length === 0) return [];

    const shortItems: Array<{ id: string; text: string; originalIndex: number }> = [];
    const longItems: Array<{ id: string; text: string; originalIndex: number }> = [];

    items.forEach((item, index) => {
      if (item.text.length < 100) {
        shortItems.push({ ...item, originalIndex: index });
      } else {
        longItems.push({ ...item, originalIndex: index });
      }
    });

    const results: Array<{ id: string; result: TranslateResult; originalIndex: number }> = [];

    if (shortItems.length > 0) {
      const combinedText = shortItems.map((item) => item.text).join('\n');
      try {
        const combinedResult = await this.translate(combinedText, options);
        const parts = combinedResult.translatedText.split('\n');
        if (parts.length === shortItems.length) {
          shortItems.forEach((item, i) => {
            results.push({
              id: item.id,
              result: { translatedText: parts[i], detectedSourceLang: combinedResult.detectedSourceLang },
              originalIndex: item.originalIndex,
            });
          });
        } else {
          for (const item of shortItems) {
            const result = await this.translate(item.text, options);
            results.push({ id: item.id, result, originalIndex: item.originalIndex });
          }
        }
      } catch {
        for (const item of shortItems) {
          const result = await this.translate(item.text, options);
          results.push({ id: item.id, result, originalIndex: item.originalIndex });
        }
      }
    }

    for (const item of longItems) {
      const result = await this.translate(item.text, options);
      results.push({ id: item.id, result, originalIndex: item.originalIndex });
    }

    results.sort((a, b) => a.originalIndex - b.originalIndex);
    return results.map(({ id, result }) => ({ id, result }));
  }

  private enqueue<T>(fn: () => Promise<T>): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      this.requestQueue.push(async () => {
        try {
          const result = await fn();
          resolve(result);
        } catch (error) {
          reject(error);
        }
      });
      this.processQueue();
    });
  }

  private async processQueue(): Promise<void> {
    if (this.isProcessing) return;
    this.isProcessing = true;

    while (this.requestQueue.length > 0) {
      const batch = this.requestQueue.splice(0, this.qpsLimit);
      await Promise.all(batch.map((fn) => fn()));
      if (this.requestQueue.length > 0) {
        await new Promise((resolve) => setTimeout(resolve, 1000));
      }
    }

    this.isProcessing = false;
  }
}
