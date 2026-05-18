import { SubtitleCue, SubtitleStyle } from '../shared/types';

const DEFAULT_STYLE: SubtitleStyle = {
  fontSize: 18,
  fontColor: '#ffffff',
  bgColor: 'rgba(0, 0, 0, 0.7)',
  position: 'bottom',
  opacity: 1,
};

export class SubtitleRenderer {
  private video: HTMLVideoElement | null = null;
  private container: HTMLElement | null = null;
  private shadowHost: HTMLElement | null = null;
  private shadowRoot: ShadowRoot | null = null;
  private subtitleOverlay: HTMLElement | null = null;
  private subtitleText: HTMLElement | null = null;
  private currentCues: SubtitleCue[] = [];
  private style: SubtitleStyle = { ...DEFAULT_STYLE };
  private timeUpdateHandler: (() => void) | null = null;
  private seekHandler: (() => void) | null = null;
  private isActive: boolean = false;

  attach(video: HTMLVideoElement, style?: SubtitleStyle): void {
    this.video = video;

    if (style) {
      this.style = { ...DEFAULT_STYLE, ...style };
    }

    let parent = video.parentElement;
    let el: HTMLElement | null = video;
    while (el) {
      const pos = window.getComputedStyle(el).position;
      if (pos && pos !== 'static') {
        parent = el;
        break;
      }
      el = el.parentElement;
    }
    if (!parent) {
      parent = video.parentElement;
    }

    this.container = parent;

    if (!this.container) return;

    const containerStyle = window.getComputedStyle(this.container);
    if (containerStyle.position === 'static') {
      this.container.style.position = 'relative';
    }

    this.shadowHost = document.createElement('div');
    this.container.appendChild(this.shadowHost);

    this.shadowRoot = this.shadowHost.attachShadow({ mode: 'open' });

    const styleEl = document.createElement('style');
    styleEl.textContent = this.createStyleContent();
    this.shadowRoot.appendChild(styleEl);

    this.subtitleOverlay = document.createElement('div');
    this.subtitleOverlay.className = 'subtitle-overlay';
    this.subtitleOverlay.style.position = 'absolute';
    this.subtitleOverlay.style.left = '50%';
    this.subtitleOverlay.style.transform = 'translateX(-50%)';
    this.subtitleOverlay.style.width = '90%';
    this.subtitleOverlay.style.textAlign = 'center';
    this.subtitleOverlay.style.pointerEvents = 'none';
    this.subtitleOverlay.style.zIndex = '9999';

    if (this.style.position === 'bottom') {
      this.subtitleOverlay.style.bottom = '10%';
      this.subtitleOverlay.style.top = '';
    } else {
      this.subtitleOverlay.style.top = '10%';
      this.subtitleOverlay.style.bottom = '';
    }

    this.subtitleText = document.createElement('span');
    this.subtitleText.className = 'subtitle-text';
    this.shadowRoot.appendChild(this.subtitleOverlay);
    this.subtitleOverlay.appendChild(this.subtitleText);

    this.applyStyle();

    this.timeUpdateHandler = () => this.onTimeUpdate();
    this.seekHandler = () => this.onSeeked();
    this.video.addEventListener('timeupdate', this.timeUpdateHandler);
    this.video.addEventListener('seeked', this.seekHandler);

    this.isActive = true;
  }

  detach(): void {
    if (this.video && this.timeUpdateHandler) {
      this.video.removeEventListener('timeupdate', this.timeUpdateHandler);
    }
    if (this.video && this.seekHandler) {
      this.video.removeEventListener('seeked', this.seekHandler);
    }

    if (this.shadowHost && this.shadowHost.parentNode) {
      this.shadowHost.parentNode.removeChild(this.shadowHost);
    }

    this.video = null;
    this.container = null;
    this.shadowHost = null;
    this.shadowRoot = null;
    this.subtitleOverlay = null;
    this.subtitleText = null;
    this.currentCues = [];
    this.style = { ...DEFAULT_STYLE };
    this.timeUpdateHandler = null;
    this.seekHandler = null;
    this.isActive = false;
  }

  updateCues(cues: SubtitleCue[]): void {
    this.currentCues = [...cues].sort((a, b) => a.startTime - b.startTime);
  }

  updateStyle(style: Partial<SubtitleStyle>): void {
    this.style = { ...this.style, ...style };
    this.applyStyle();
  }

  show(): void {
    if (this.subtitleOverlay) {
      this.subtitleOverlay.style.display = '';
    }
  }

  hide(): void {
    if (this.subtitleOverlay) {
      this.subtitleOverlay.style.display = 'none';
    }
  }

  private renderSubtitle(): void {
    if (!this.video || !this.subtitleText) return;

    const currentTime = this.video.currentTime;
    let matchedCue: SubtitleCue | null = null;

    if (this.currentCues.length > 0) {
      let lo = 0;
      let hi = this.currentCues.length - 1;
      while (lo <= hi) {
        const mid = (lo + hi) >>> 1;
        const cue = this.currentCues[mid];
        if (cue.startTime <= currentTime) {
          if (cue.endTime >= currentTime) {
            matchedCue = cue;
            break;
          }
          lo = mid + 1;
        } else {
          hi = mid - 1;
        }
      }
    }

    if (matchedCue) {
      this.subtitleText.textContent = matchedCue.translatedText || matchedCue.text;
    } else {
      this.subtitleText.textContent = '';
    }
  }

  private applyStyle(): void {
    if (!this.subtitleText || !this.subtitleOverlay) return;

    this.subtitleText.style.fontSize = this.style.fontSize + 'px';
    this.subtitleText.style.color = this.style.fontColor;
    this.subtitleText.style.backgroundColor = this.style.bgColor;
    this.subtitleText.style.opacity = String(this.style.opacity);

    if (this.style.position === 'bottom') {
      this.subtitleOverlay.style.bottom = '10%';
      this.subtitleOverlay.style.top = '';
    } else {
      this.subtitleOverlay.style.top = '10%';
      this.subtitleOverlay.style.bottom = '';
    }
  }

  private onTimeUpdate(): void {
    this.renderSubtitle();
  }

  private onSeeked(): void {
    this.renderSubtitle();
  }

  private createStyleContent(): string {
    return `:host {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 9999;
}
.subtitle-overlay {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  width: 90%;
  text-align: center;
  pointer-events: none;
}
.subtitle-text {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 4px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}`;
  }
}
