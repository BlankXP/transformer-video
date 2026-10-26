import { useState } from "react";
import type { TaskDetail } from "../types";
import { downloadFile, type DownloadKind } from "../api/client";

interface Props {
  taskId: string | null;
  task?: TaskDetail | null;
}

const BUTTON_BASE =
  "flex-1 min-w-[120px] py-2.5 text-center text-white font-medium rounded-lg transition disabled:bg-gray-300 disabled:cursor-not-allowed";

const FILENAME_SUFFIX: Record<DownloadKind, string> = {
  video: "video.mp4",
  burned: "burned.mp4",
  srt: "subtitles.srt",
  txt: "recognized.txt",
};

interface Action {
  kind: DownloadKind;
  label: string;
  className: string;
}

/**
 * 下载面板。
 *
 * <p>按后端下发的 has_* 布尔逐个渲染，而不是依赖「字幕数组非空」——
 * 「仅下载、不翻译」模式没有任何字幕，此前整个面板被隐藏，用户拿不到视频。
 */
function DownloadPanel({ taskId, task }: Props) {
  const [busy, setBusy] = useState<DownloadKind | null>(null);
  const [error, setError] = useState<string | null>(null);

  // 收窄成 const，闭包里才能保持 string 类型
  const id = taskId;
  if (!id || !task) {
    return null;
  }

  const actions: Action[] = [];
  if (task.has_video) {
    actions.push({ kind: "video", label: "下载视频", className: "bg-slate-700 hover:bg-slate-800" });
  }
  if (task.has_burned_video) {
    actions.push({ kind: "burned", label: "下载烧录视频", className: "bg-purple-600 hover:bg-purple-700" });
  }
  if (task.has_srt) {
    actions.push({ kind: "srt", label: "下载 SRT 字幕", className: "bg-green-600 hover:bg-green-700" });
  }
  if (task.has_recognized_text) {
    actions.push({ kind: "txt", label: "识别文本 TXT", className: "bg-blue-600 hover:bg-blue-700" });
  }

  if (actions.length === 0) {
    return null;
  }

  const handleDownload = async (kind: DownloadKind) => {
    setBusy(kind);
    setError(null);
    try {
      await downloadFile(id, kind, `${id}_${FILENAME_SUFFIX[kind]}`);
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
        {actions.map((action) => (
          <button
            key={action.kind}
            onClick={() => handleDownload(action.kind)}
            disabled={busy !== null}
            className={`${BUTTON_BASE} ${action.className}`}
          >
            {busy === action.kind ? "下载中..." : action.label}
          </button>
        ))}
      </div>
      {error && <p className="text-xs text-red-500 mt-2">{error}</p>}
    </div>
  );
}

export default DownloadPanel;
