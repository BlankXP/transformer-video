import { SubtitleTrack, SubtitleCue, Settings, Message } from '../shared/types';
import { DEFAULT_SETTINGS } from '../shared/constants';
import { sendMessage, onMessage } from '../shared/message';
import { getSettings } from '../shared/storage';
import { SubtitleDetector } from './subtitle-detector';
import { AudioCapture } from './audio-capture';
import { SubtitleRenderer } from './subtitle-renderer';

class VideoTranslatorContentScript {
  private video: HTMLVideoElement | null = null;
  private videoId: string = '';
  private subtitleDetector: SubtitleDetector = new SubtitleDetector();
  private audioCapture: AudioCapture = new AudioCapture();
  private subtitleRenderer: SubtitleRenderer = new SubtitleRenderer();
  private isTranslating: boolean = false;
  private wasTranslatingBeforeOffline: boolean = false;
  private hasSubtitle: boolean = false;
  private subtitleSource: string = '';
  private currentTracks: SubtitleTrack[] = [];
  private settings: Settings = { ...DEFAULT_SETTINGS };
  private liveCues: SubtitleCue[] = [];

  init(): void {
    this.detectVideo();
    onMessage((message: Message) => {
      switch (message.type) {
        case 'TOGGLE_TRANSLATION':
          this.handleToggleTranslation(message.payload.enabled);
          break;
        case 'UPDATE_SETTINGS':
          this.handleUpdateSettings(message.payload);
          break;
        case 'GET_STATUS':
          return this.handleGetStatus();
      }
    });
    window.addEventListener('offline', () => this.handleOffline());
    window.addEventListener('online', () => this.handleOnline());
  }

  private detectVideo(): void {
    const video = document.querySelector('video');
    if (video) {
      this.video = video;
      this.videoId = `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
      this.detectSubtitles().then(() => {
        sendMessage({
          type: 'VIDEO_DETECTED',
          payload: { videoId: this.videoId, hasSubtitle: this.hasSubtitle },
        });
      });
    }
    const observer = new MutationObserver(() => {
      if (!this.video) {
        const v = document.querySelector('video');
        if (v) {
          this.video = v;
          this.videoId = `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
          this.detectSubtitles().then(() => {
            sendMessage({
              type: 'VIDEO_DETECTED',
              payload: { videoId: this.videoId, hasSubtitle: this.hasSubtitle },
            });
          });
        }
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });
  }

  private async detectSubtitles(): Promise<void> {
    if (!this.video) return;
    const tracks = await this.subtitleDetector.detectSubtitles(this.video);
    this.currentTracks = tracks;
    if (tracks.length > 0) {
      this.hasSubtitle = true;
      this.subtitleSource = tracks[0].source;
    } else {
      this.hasSubtitle = false;
    }
  }

  private async startTranslation(): Promise<void> {
    this.settings = await getSettings();
    if (this.hasSubtitle) {
      await this.translateWithSubtitle();
    } else {
      await this.translateWithAudio();
    }
  }

  private stopTranslation(): void {
    this.isTranslating = false;
    this.audioCapture.stop();
    this.subtitleRenderer.hide();
    this.subtitleDetector.stopDOMObservation();
    this.liveCues = [];
  }

  private getSourceLang(override?: string): string | undefined {
    if (this.settings.autoDetectSource) return override;
    return this.settings.defaultSourceLang || override;
  }

  private async translateWithSubtitle(): Promise<void> {
    this.isTranslating = true;
    this.subtitleRenderer.detach();
    if (this.video) {
      this.subtitleRenderer.attach(this.video, this.settings.subtitleStyle);
    }
    this.subtitleRenderer.show();

    if (this.subtitleSource === 'dom') {
      this.subtitleDetector.startDOMObservation(this.video!, async (text) => {
        if (!this.isTranslating) return;
        if (this.liveCues.length > 0) {
          this.liveCues[this.liveCues.length - 1].endTime = this.video!.currentTime;
        }
        try {
          const result = await sendMessage<{ translatedText: string }>({
            type: 'TRANSLATE_REQUEST',
            payload: {
              text,
              sourceLang: this.getSourceLang(),
              targetLang: this.settings.defaultTargetLang,
            },
          });
          const cue: SubtitleCue = {
            id: `dom-${Date.now()}`,
            startTime: this.video!.currentTime,
            endTime: this.video!.currentTime + 3600,
            text,
            translatedText: result.translatedText,
          };
          this.liveCues.push(cue);
          this.subtitleRenderer.updateCues([...this.liveCues]);
        } catch {}
      });
    } else {
      const allCues: SubtitleCue[] = [];
      for (const track of this.currentTracks) {
        allCues.push(...track.cues);
      }

      const batchSize = 10;
      for (let i = 0; i < allCues.length; i += batchSize) {
        if (!this.isTranslating) break;
        const batch = allCues.slice(i, i + batchSize);
        const results = await Promise.all(
          batch.map((cue) =>
            sendMessage<{ translatedText: string }>({
              type: 'TRANSLATE_REQUEST',
              payload: {
                text: cue.text,
                sourceLang: this.getSourceLang(this.currentTracks[0]?.language),
                targetLang: this.settings.defaultTargetLang,
              },
            })
          )
        );
        for (let j = 0; j < batch.length; j++) {
          batch[j].translatedText = results[j].translatedText;
        }
      }

      this.subtitleRenderer.updateCues(allCues);
    }
  }

  private async translateWithAudio(): Promise<void> {
    this.isTranslating = true;
    this.subtitleRenderer.detach();
    if (this.video) {
      this.subtitleRenderer.attach(this.video, this.settings.subtitleStyle);
    }
    this.subtitleRenderer.show();

    this.audioCapture.setSegmentDuration(this.settings.segmentDuration * 1000);
    this.audioCapture.setOnSegment(async (blob, timestamp, index) => {
      if (!this.isTranslating) return;
      try {
        const base64 = await this.blobToBase64(blob);
        const asrResult = await sendMessage<{ text: string; language: string }>({
          type: 'AUDIO_SEGMENT',
          payload: {
            videoId: this.videoId,
            audioBase64: base64,
            timestamp,
            index,
          },
        });
        const translateResult = await sendMessage<{ translatedText: string }>({
          type: 'TRANSLATE_REQUEST',
          payload: {
            text: asrResult.text,
            sourceLang: this.getSourceLang(asrResult.language),
            targetLang: this.settings.defaultTargetLang,
          },
        });
        const cue: SubtitleCue = {
          id: `asr-${index}`,
          startTime: timestamp / 1000,
          endTime: timestamp / 1000 + this.settings.segmentDuration,
          text: asrResult.text,
          translatedText: translateResult.translatedText,
        };
        this.liveCues.push(cue);
        this.subtitleRenderer.updateCues([...this.liveCues]);
      } catch {}
    });
    this.audioCapture.start(this.video!);
  }

  private handleToggleTranslation(enabled: boolean): void {
    if (enabled) {
      this.startTranslation();
    } else {
      this.stopTranslation();
    }
  }

  private handleOffline(): void {
    if (this.isTranslating) {
      this.wasTranslatingBeforeOffline = true;
      this.stopTranslation();
    }
  }

  private handleOnline(): void {
    if (this.wasTranslatingBeforeOffline) {
      this.wasTranslatingBeforeOffline = false;
      this.startTranslation();
    }
  }

  private handleUpdateSettings(settings: Partial<Settings>): void {
    this.settings = {
      ...this.settings,
      ...settings,
      subtitleStyle: settings.subtitleStyle
        ? { ...this.settings.subtitleStyle, ...settings.subtitleStyle }
        : this.settings.subtitleStyle,
    };
    if (this.isTranslating) {
      this.stopTranslation();
      this.startTranslation();
    }
    sendMessage({
      type: 'UPDATE_SETTINGS',
      payload: settings,
    }).catch(() => {});
  }

  private handleGetStatus(): { videoDetected: boolean; hasSubtitle: boolean; subtitleSource?: string; translating: boolean } {
    return {
      videoDetected: !!this.video,
      hasSubtitle: this.hasSubtitle,
      subtitleSource: this.subtitleSource || undefined,
      translating: this.isTranslating,
    };
  }

  private blobToBase64(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onloadend = () => {
        const result = reader.result as string;
        resolve(result.split(',')[1]);
      };
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });
  }
}

const contentScript = new VideoTranslatorContentScript();
contentScript.init();
