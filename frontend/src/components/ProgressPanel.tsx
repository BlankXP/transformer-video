import type { TaskStage } from "../types";

interface Props {
  stage: TaskStage | null;
  progress: number;
  message: string;
}

const STAGES: { key: TaskStage; label: string }[] = [
  { key: "downloading", label: "下载视频" },
  { key: "extracting_audio", label: "提取音频" },
  { key: "recognizing", label: "语音识别" },
  { key: "translating", label: "翻译字幕" },
  { key: "generating_subtitle", label: "生成字幕" },
];

function ProgressPanel({ stage, progress, message }: Props) {
  if (!stage) {
    return (
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
        <p className="text-gray-400 text-center">等待任务开始...</p>
      </div>
    );
  }

  const stageIndex = STAGES.findIndex((s) => s.key === stage);
  const isCompleted = stage === "completed";
  const isFailed = stage === "failed";

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 space-y-3">
      <h3 className="text-sm font-semibold text-gray-700">处理进度</h3>
      {STAGES.map((s, i) => {
        const isDone = isCompleted || i < stageIndex;
        const isCurrent = s.key === stage && !isCompleted && !isFailed;
        return (
          <div key={s.key} className="flex items-center gap-2">
            <span className={`w-5 h-5 flex items-center justify-center rounded-full text-xs ${
              isDone ? "bg-green-500 text-white" : isCurrent ? "bg-blue-500 text-white animate-pulse" : "bg-gray-200 text-gray-400"
            }`}>
              {isDone ? "✓" : i + 1}
            </span>
            <span className={`text-sm ${isDone ? "text-green-600" : isCurrent ? "text-blue-600 font-medium" : "text-gray-400"}`}>
              {s.label}
            </span>
          </div>
        );
      })}
      {!isCompleted && !isFailed && (
        <div className="mt-3">
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div
              className="bg-blue-500 h-2 rounded-full transition-all duration-300"
              style={{ width: `${Math.round(progress * 100)}%` }}
            />
          </div>
          <p className="text-xs text-gray-500 mt-1">{message}</p>
        </div>
      )}
      {isCompleted && <p className="text-green-600 text-sm font-medium">✅ 处理完成！</p>}
      {isFailed && <p className="text-red-600 text-sm font-medium">❌ 处理失败</p>}
    </div>
  );
}

export default ProgressPanel;
