import { SubtitleCue, SubtitleTrack } from '../shared/types';

export class BilibiliSubtitleExtractor {
  static isBilibiliPage(): boolean {
    return window.location.hostname.includes('bilibili.com');
  }

  static async extractSubtitles(): Promise<SubtitleTrack[]> {
    const tracks: SubtitleTrack[] = [];

    try {
      const { bvid, cid } = BilibiliSubtitleExtractor.getVideoParams();
      if (!bvid || !cid) {
        return tracks;
      }

      const apiUrl = `https://api.bilibili.com/x/player/v2?bvid=${bvid}&cid=${cid}`;
      const response = await fetch(apiUrl, { credentials: 'include' });
      const data = await response.json();

      const subtitleItems = data?.data?.subtitle?.items;
      if (!subtitleItems || subtitleItems.length === 0) {
        return tracks;
      }

      for (const item of subtitleItems) {
        try {
          const subtitleUrl = item.subtitle_url.startsWith('//')
            ? `https:${item.subtitle_url}`
            : item.subtitle_url;
          const subResponse = await fetch(subtitleUrl);
          const subJson = await subResponse.json();
          const cues = BilibiliSubtitleExtractor.parseBilibiliJSON(subJson);
          tracks.push({
            language: item.lan || 'unknown',
            label: item.lan_doc || item.lan || 'Unknown',
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

  private static getVideoParams(): { bvid: string | null; cid: number | null } {
    let bvid: string | null = null;
    let cid: number | null = null;

    try {
      const match = window.location.pathname.match(/\/video\/(BV[\w]+)/);
      if (match) {
        bvid = match[1];
      }
    } catch {}

    try {
      const playinfo = (window as any).__playinfo__;
      if (playinfo?.data?.cid) {
        cid = playinfo.data.cid;
      }
      if (!bvid && playinfo?.data?.bvid) {
        bvid = playinfo.data.bvid;
      }
    } catch {}

    if (bvid && !cid) {
      try {
        const scripts = document.querySelectorAll('script');
        for (const script of scripts) {
          const content = script.textContent || '';
          const cidMatch = content.match(/"cid"\s*:\s*(\d+)/);
          if (cidMatch) {
            cid = parseInt(cidMatch[1], 10);
            break;
          }
        }
      } catch {}
    }

    return { bvid, cid };
  }

  private static parseBilibiliJSON(json: any): SubtitleCue[] {
    const cues: SubtitleCue[] = [];
    const body = json.body || json;

    if (!Array.isArray(body)) {
      return cues;
    }

    let counter = 0;
    for (const item of body) {
      if (item.from !== undefined && item.to !== undefined && item.content) {
        cues.push({
          id: `bili-${counter++}`,
          startTime: item.from,
          endTime: item.to,
          text: item.content,
        });
      }
    }

    return cues;
  }
}
