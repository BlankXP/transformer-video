import { useState } from "react";
import type { SubtitleEntry, TaskResult } from "../types";
import { downloadFile, type DownloadKind } from "../api/client";

interface Props {
  taskId: string | null;
  subtitles: SubtitleEntry[];
  result?: TaskResult;
}

const BUTTON_BASE =
  "flex-1 min-w-[120px] py-2.5 text-center text-white font-medium rounded-lg transition disabled:bg-gray-300 disabled:cursor-not-allowed";

function DownloadPanel({ taskId, subtitles, result }: Props) {
  const [busy, setBusy] = useState<DownloadKind | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!taskId || subtitles.length === 0) {
    return null;
  }

  const hasBurnedVideo = result?.burned_video_path;

  const handleDownload = async (kind: DownloadKind, filename: string) => {
    setBusy(kind);
    setError(null);
    try {
      await downloadFile(taskId, kind, filename);
    } catch (e) {
      setError(e instanceof Error ? e.message : "下载失败");
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
      <h3 className="text-sm font-semibold text-gray-700">下载</h3>
      <div className="flex gap-3 mt-3 flex-wrap">
        <button
          onClick={() => handleDownload("srt", `${taskId}_subtitles.srt`)}
          disabled={busy !== null}
          className={`${BUTTON_BASE} bg-green-600 hover:bg-green-700`}
        >
          {busy === "srt" ? "下载中..." : "下载 SRT 字幕"}
        </button>
        <button
          onClick={() => handleDownload("txt", `${taskId}_recognized.txt`)}
          disabled={busy !== null}
          className={`${BUTTON_BASE} bg-blue-600 hover:bg-blue-700`}
        >
          {busy === "txt" ? "下载中..." : "识别文本 TXT"}
        </button>
        {hasBurnedVideo && (
          <button
            onClick={() => handleDownload("burned", `${taskId}_burned.mp4`)}
            disabled={busy !== null}
            className={`${BUTTON_BASE} bg-purple-600 hover:bg-purple-700`}
          >
            {busy === "burned" ? "下载中..." : "下载烧录视频"}
          </button>
        )}
      </div>
      {error && <p className="text-xs text-red-500 mt-2">{error}</p>}
    </div>
  );
}

export default DownloadPanel;
