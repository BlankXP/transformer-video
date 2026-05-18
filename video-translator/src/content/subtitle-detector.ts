import { SubtitleCue, SubtitleTrack } from '../shared/types';
import { YouTubeSubtitleExtractor } from '../platforms/youtube';
import { BilibiliSubtitleExtractor } from '../platforms/bilibili';

export class SubtitleDetector {
  private cueCounter = 0;
  private observer: MutationObserver | null = null;

  async detectSubtitles(video: HTMLVideoElement): Promise<SubtitleTrack[]> {
    const trackResults = await this.detectTrackElements(video);
    if (trackResults.length > 0) {
      return trackResults;
    }

    const platformResults = await this.detectPlatformSubtitles(video);
    if (platformResults.length > 0) {
      return platformResults;
    }

    const domResults = await this.detectDOMSubtitles(video);
    return domResults;
  }

  private async detectTrackElements(video: HTMLVideoElement): Promise<SubtitleTrack[]> {
    const tracks: SubtitleTrack[] = [];
    const trackElements = video.querySelectorAll('track');

    for (const trackEl of trackElements) {
      const kind = trackEl.kind;
      if (kind !== 'subtitles' && kind !== 'captions') {
        continue;
      }

      const src = trackEl.src;
      if (!src) {
        continue;
      }

      try {
        const response = await fetch(src);
        const text = await response.text();
        const cues = this.parseWebVTT(text);
        tracks.push({
          language: trackEl.srclang || 'unknown',
          label: trackEl.label || trackEl.srclang || 'Unknown',
          cues,
          source: 'track',
        });
      } catch {
        continue;
      }
    }

    return tracks;
  }

  private parseWebVTT(text: string): SubtitleCue[] {
    const cues: SubtitleCue[] = [];
    const cleaned = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
    const blocks = cleaned.split(/\n\n+/);

    for (const block of blocks) {
      const lines = block.trim().split('\n');
      if (lines.length < 2) {
        continue;
      }

      let timeLineIndex = -1;
      for (let i = 0; i < lines.length; i++) {
        if (lines[i].includes('-->')) {
          timeLineIndex = i;
          break;
        }
      }

      if (timeLineIndex === -1) {
        continue;
      }

      const timeLine = lines[timeLineIndex];
      const timeMatch = timeLine.match(
        /(\d{1,2}:)?(\d{2}):(\d{2})[.,](\d{3})\s*-->\s*(\d{1,2}:)?(\d{2}):(\d{2})[.,](\d{3})/
      );

      if (!timeMatch) {
        continue;
      }

      const startTime = this.parseTimeString(timeMatch[1], timeMatch[2], timeMatch[3], timeMatch[4]);
      const endTime = this.parseTimeString(timeMatch[5], timeMatch[6], timeMatch[7], timeMatch[8]);

      const textLines = lines.slice(timeLineIndex + 1);
      const cueText = textLines.join('\n').replace(/<[^>]*>/g, '').trim();

      if (cueText) {
        cues.push({
          id: `vtt-${this.cueCounter++}`,
          startTime,
          endTime,
          text: cueText,
        });
      }
    }

    return cues;
  }

  private parseTimeString(
    hours: string | undefined,
    minutes: string,
    seconds: string,
    millis: string
  ): number {
    const h = hours ? parseInt(hours.replace(':', ''), 10) : 0;
    const m = parseInt(minutes, 10);
    const s = parseInt(seconds, 10);
    const ms = parseInt(millis, 10);
    return h * 3600 + m * 60 + s + ms / 1000;
  }

  private async detectPlatformSubtitles(_video: HTMLVideoElement): Promise<SubtitleTrack[]> {
    if (YouTubeSubtitleExtractor.isYouTubePage()) {
      return YouTubeSubtitleExtractor.extractSubtitles();
    }

    if (BilibiliSubtitleExtractor.isBilibiliPage()) {
      return BilibiliSubtitleExtractor.extractSubtitles();
    }

    return [];
  }

  private async detectDOMSubtitles(video: HTMLVideoElement): Promise<SubtitleTrack[]> {
    return [
      {
        language: 'unknown',
        label: 'DOM Subtitles (Live)',
        cues: [],
        source: 'dom',
      },
    ];
  }

  startDOMObservation(video: HTMLVideoElement, callback: (text: string) => void): void {
    this.stopDOMObservation();

    const container = video.closest('.html5-video-container') || video.parentElement || document.body;

    const youtubeSelector = '.ytp-caption-segment';
    const genericSelectors = [
      '.caption-visual-line',
      '.captions-text',
      '.subtitle-text',
      '[class*="caption"]',
      '[class*="subtitle"]',
    ];
    const allSelectors = [youtubeSelector, ...genericSelectors].join(', ');

    this.observer = new MutationObserver(() => {
      const elements = container.querySelectorAll(allSelectors);
      const texts: string[] = [];
      for (const el of elements) {
        const text = el.textContent?.trim();
        if (text) {
          texts.push(text);
        }
      }

      if (texts.length > 0) {
        callback(texts.join(' '));
      }
    });

    this.observer.observe(container, {
      childList: true,
      subtree: true,
      characterData: true,
    });
  }

  stopDOMObservation(): void {
    if (this.observer) {
      this.observer.disconnect();
      this.observer = null;
    }
  }
}
