import type { SubtitleEntry, TaskResult } from "../types";
import { getSubtitleSrtUrl, getRecognizedTxtUrl, getBurnedVideoUrl } from "../api/client";

interface Props {
  taskId: string | null;
  subtitles: SubtitleEntry[];
  result?: TaskResult;
}

function DownloadPanel({ taskId, subtitles, result }: Props) {
  if (!taskId || subtitles.length === 0) {
    return null;
  }

  const srtUrl = getSubtitleSrtUrl(taskId);
  const txtUrl = getRecognizedTxtUrl(taskId);
  const hasBurnedVideo = result?.burned_video_path;

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
      <h3 className="text-sm font-semibold text-gray-700">下载</h3>
      <div className="flex gap-3 mt-3 flex-wrap">
        <a
          href={srtUrl}
          download
          className="flex-1 min-w-[120px] py-2.5 text-center bg-green-600 text-white font-medium rounded-lg hover:bg-green-700 transition"
        >
          下载 SRT 字幕
        </a>
        <a
          href={txtUrl}
          download
          className="flex-1 min-w-[120px] py-2.5 text-center bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 transition"
        >
          识别文本 TXT
        </a>
        {hasBurnedVideo && (
          <a
            href={getBurnedVideoUrl(taskId)}
            download
            className="flex-1 min-w-[120px] py-2.5 text-center bg-purple-600 text-white font-medium rounded-lg hover:bg-purple-700 transition"
          >
            下载烧录视频
          </a>
        )}
      </div>
    </div>
  );
}

export default DownloadPanel;
