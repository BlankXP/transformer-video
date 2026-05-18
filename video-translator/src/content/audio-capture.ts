export class AudioCapture {
  private audioContext: AudioContext | null = null;
  private mediaSource: MediaElementAudioSourceNode | null = null;
  private mediaRecorder: MediaRecorder | null = null;
  private chunks: Blob[] = [];
  private isCapturing: boolean = false;
  private segmentDuration: number = 8000;
  private onSegmentCallback: ((blob: Blob, timestamp: number, index: number) => void) | null = null;
  private segmentIndex: number = 0;
  private segmentTimer: number | null = null;
  private video: HTMLVideoElement | null = null;
  private boundOnPause: (() => void) | null = null;
  private boundOnPlay: (() => void) | null = null;
  private boundOnSeeked: (() => void) | null = null;

  start(video: HTMLVideoElement, segmentDuration?: number): void {
    if (this.isCapturing) {
      this.stop();
    }

    if (segmentDuration !== undefined) {
      this.segmentDuration = segmentDuration;
    }

    this.video = video;
    this.segmentIndex = 0;
    this.chunks = [];

    try {
      this.audioContext = new AudioContext();
    } catch {
      throw new Error('Failed to create AudioContext');
    }

    try {
      this.mediaSource = this.audioContext.createMediaElementSource(video);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      if (msg.includes('CORS') || msg.includes('cross-origin')) {
        throw new Error('CORS: Cannot capture audio from cross-origin video');
      }
      throw e;
    }

    const destination = this.audioContext.createMediaStreamDestination();
    this.mediaSource.connect(destination);
    this.mediaSource.connect(this.audioContext.destination);

    let mimeType = 'audio/webm;codecs=opus';
    if (!MediaRecorder.isTypeSupported(mimeType)) {
      mimeType = 'audio/wav';
    }

    this.mediaRecorder = new MediaRecorder(destination.stream, { mimeType });

    this.mediaRecorder.ondataavailable = (e: BlobEvent) => {
      if (e.data.size > 0) {
        this.chunks.push(e.data);
      }
    };

    this.mediaRecorder.onstop = () => {
      if (this.chunks.length > 0) {
        const blob = new Blob(this.chunks, { type: mimeType });
        const timestamp = this.video ? this.video.currentTime * 1000 : 0;
        if (this.onSegmentCallback) {
          this.onSegmentCallback(blob, timestamp, this.segmentIndex);
        }
        this.segmentIndex++;
        this.chunks = [];
      }

      if (this.isCapturing && this.mediaRecorder && this.mediaRecorder.state !== 'recording') {
        try {
          this.mediaRecorder.start();
          this.scheduleSegment();
        } catch {
          // recorder may have been stopped entirely
        }
      }
    };

    this.isCapturing = true;
    this.mediaRecorder.start();
    this.scheduleSegment();

    this.boundOnPause = () => {
      if (this.mediaRecorder && this.mediaRecorder.state === 'recording') {
        this.mediaRecorder.pause();
        this.clearSegmentTimer();
      }
    };

    this.boundOnPlay = () => {
      if (this.mediaRecorder && this.mediaRecorder.state === 'paused') {
        this.mediaRecorder.resume();
        this.scheduleSegment();
      }
    };

    this.boundOnSeeked = () => {
      this.resetSegment();
    };

    video.addEventListener('pause', this.boundOnPause);
    video.addEventListener('play', this.boundOnPlay);
    video.addEventListener('seeked', this.boundOnSeeked);
  }

  private scheduleSegment(): void {
    this.clearSegmentTimer();
    this.segmentTimer = window.setTimeout(() => {
      if (this.mediaRecorder && this.mediaRecorder.state === 'recording') {
        this.mediaRecorder.stop();
      }
    }, this.segmentDuration);
  }

  private clearSegmentTimer(): void {
    if (this.segmentTimer !== null) {
      window.clearTimeout(this.segmentTimer);
      this.segmentTimer = null;
    }
  }

  private resetSegment(): void {
    this.chunks = [];
    if (this.mediaRecorder && this.mediaRecorder.state === 'recording') {
      this.mediaRecorder.stop();
    }
  }

  stop(): void {
    this.isCapturing = false;
    this.clearSegmentTimer();

    if (this.mediaRecorder) {
      if (this.mediaRecorder.state === 'recording' || this.mediaRecorder.state === 'paused') {
        this.mediaRecorder.onstop = null;
        this.mediaRecorder.stop();
      }
      this.mediaRecorder = null;
    }

    if (this.video) {
      if (this.boundOnPause) {
        this.video.removeEventListener('pause', this.boundOnPause);
      }
      if (this.boundOnPlay) {
        this.video.removeEventListener('play', this.boundOnPlay);
      }
      if (this.boundOnSeeked) {
        this.video.removeEventListener('seeked', this.boundOnSeeked);
      }
      this.video = null;
    }

    if (this.mediaSource) {
      this.mediaSource.disconnect();
      this.mediaSource = null;
    }

    if (this.audioContext) {
      this.audioContext.close();
      this.audioContext = null;
    }

    this.chunks = [];
    this.segmentIndex = 0;
    this.boundOnPause = null;
    this.boundOnPlay = null;
    this.boundOnSeeked = null;
  }

  setOnSegment(callback: (blob: Blob, timestamp: number, index: number) => void): void {
    this.onSegmentCallback = callback;
  }

  setSegmentDuration(duration: number): void {
    this.segmentDuration = duration;
  }
}
