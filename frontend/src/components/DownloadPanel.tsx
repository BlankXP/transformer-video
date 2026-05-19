import type { SubtitleEntry } from "../types";
import { getSubtitleSrtUrl, getVideoDownloadUrl } from "../api/client";

interface Props {
  taskId: string | null;
  subtitles: SubtitleEntry[];
  onBurn: () => void;
  burning: boolean;
}

function DownloadPanel({ taskId, subtitles, onBurn, burning }: Props) {
  if (!taskId || subtitles.length === 0) {
    return null;
  }

  const srtUrl = getSubtitleSrtUrl(taskId);
  const videoUrl = getVideoDownloadUrl(taskId);

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-gray-700">下载</h3>
        <button
          onClick={onBurn}
          disabled={burning}
          className="px-3 py-1.5 text-sm bg-orange-500 text-white rounded-lg hover:bg-orange-600 disabled:bg-gray-300 disabled:cursor-not-allowed transition"
        >
          {burning ? "烧录中..." : "重新烧录字幕"}
        </button>
      </div>
      <div className="flex gap-3 mt-3">
        <a
          href={srtUrl}
          download
          className="flex-1 py-2.5 text-center bg-green-600 text-white font-medium rounded-lg hover:bg-green-700 transition"
        >
          下载 SRT 字幕
        </a>
        <a
          href={videoUrl}
          download
          className="flex-1 py-2.5 text-center bg-purple-600 text-white font-medium rounded-lg hover:bg-purple-700 transition"
        >
          下载带字幕视频
        </a>
      </div>
    </div>
  );
}

export default DownloadPanel;
