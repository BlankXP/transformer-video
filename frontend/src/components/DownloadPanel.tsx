import type { SubtitleEntry } from "../types";
import { getSubtitleSrtUrl, getRecognizedTxtUrl } from "../api/client";

interface Props {
  taskId: string | null;
  subtitles: SubtitleEntry[];
}

function DownloadPanel({ taskId, subtitles }: Props) {
  if (!taskId || subtitles.length === 0) {
    return null;
  }

  const srtUrl = getSubtitleSrtUrl(taskId);
  const txtUrl = getRecognizedTxtUrl(taskId);

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
      <h3 className="text-sm font-semibold text-gray-700">下载</h3>
      <div className="flex gap-3 mt-3">
        <a
          href={srtUrl}
          download
          className="flex-1 py-2.5 text-center bg-green-600 text-white font-medium rounded-lg hover:bg-green-700 transition"
        >
          下载 SRT 字幕
        </a>
        <a
          href={txtUrl}
          download
          className="flex-1 py-2.5 text-center bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 transition"
        >
          识别文本 TXT
        </a>
      </div>
    </div>
  );
}

export default DownloadPanel;
