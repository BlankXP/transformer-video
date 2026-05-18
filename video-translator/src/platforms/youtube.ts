import { SubtitleCue, SubtitleTrack } from '../shared/types';

export class YouTubeSubtitleExtractor {
  static isYouTubePage(): boolean {
    return window.location.hostname.includes('youtube.com');
  }

  static async extractSubtitles(): Promise<SubtitleTrack[]> {
    const tracks: SubtitleTrack[] = [];

    try {
      const captionTracks = YouTubeSubtitleExtractor.getCaptionTracks();
      if (!captionTracks || captionTracks.length === 0) {
        return tracks;
      }

      for (const track of captionTracks) {
        try {
          const response = await fetch(track.baseUrl);
          const xml = await response.text();
          const cues = YouTubeSubtitleExtractor.parseYouTubeXML(xml);
          tracks.push({
            language: track.languageCode || 'unknown',
            label: track.name?.simpleText || track.languageCode || 'Unknown',
            cues,
            source: 'platform',
          });
        } catch {
          continue;
        }
      }
    } catch {
      return tracks;
    }

    return tracks;
  }

  private static getCaptionTracks(): any[] | null {
    try {
      const ytInitialPlayerResponse = (window as any).ytInitialPlayerResponse;
      if (ytInitialPlayerResponse?.captions?.playerCaptionsTracklistRenderer?.captionTracks) {
        return ytInitialPlayerResponse.captions.playerCaptionsTracklistRenderer.captionTracks;
      }

      const ytplayer = (window as any).ytplayer;
      if (ytplayer?.config?.args?.player_response) {
        const playerResponse = JSON.parse(ytplayer.config.args.player_response);
        if (playerResponse?.captions?.playerCaptionsTracklistRenderer?.captionTracks) {
          return playerResponse.captions.playerCaptionsTracklistRenderer.captionTracks;
        }
      }
    } catch {
      return null;
    }

    return null;
  }

  private static parseYouTubeXML(xml: string): SubtitleCue[] {
    const cues: SubtitleCue[] = [];
    const parser = new DOMParser();
    const doc = parser.parseFromString(xml, 'text/xml');
    const textElements = doc.querySelectorAll('text');

    let counter = 0;
    for (const el of textElements) {
      const start = parseFloat(el.getAttribute('start') || '0');
      const dur = parseFloat(el.getAttribute('dur') || '0');
      const text = (el.textContent || '').replace(/<[^>]*>/g, '').trim();

      if (text) {
        cues.push({
          id: `yt-${counter++}`,
          startTime: start,
          endTime: start + dur,
          text,
        });
      }
    }

    return cues;
  }
}
