import { useState } from "react";
import type { SubtitleEntry } from "../types";

interface Props {
  subtitles: SubtitleEntry[];
  onSave: (subtitles: SubtitleEntry[]) => void;
  disabled: boolean;
}

function SubtitleEditor({ subtitles, onSave, disabled }: Props) {
  const [prevSubtitles, setPrevSubtitles] = useState(subtitles);
  const [editedSubtitles, setEditedSubtitles] = useState<SubtitleEntry[]>(subtitles);

  if (prevSubtitles !== subtitles) {
    setPrevSubtitles(subtitles);
    setEditedSubtitles(subtitles);
  }

  const handleTranslatedTextChange = (index: number, value: string) => {
    const updated = [...editedSubtitles];
    updated[index] = { ...updated[index], translated_text: value };
    setEditedSubtitles(updated);
  };

  if (editedSubtitles.length === 0) {
    return (
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
        <p className="text-gray-400 text-center">等待字幕生成...</p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-gray-700">字幕编辑</h3>
        <button
          onClick={() => onSave(editedSubtitles)}
          disabled={disabled}
          className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition"
        >
          保存修改
        </button>
      </div>
      <div className="max-h-80 overflow-y-auto space-y-2">
        {editedSubtitles.map((sub, i) => (
          <div key={sub.index} className="border border-gray-100 rounded-lg p-3 space-y-1.5">
            <div className="flex items-center gap-2 text-xs text-gray-400">
              <span className="font-medium">#{sub.index}</span>
              <span>{sub.start_time} → {sub.end_time}</span>
            </div>
            <p className="text-sm text-gray-700">{sub.source_text}</p>
            <textarea
              value={sub.translated_text}
              onChange={(e) => handleTranslatedTextChange(i, e.target.value)}
              className="w-full text-sm px-2 py-1 border border-gray-200 rounded focus:ring-1 focus:ring-blue-500 outline-none resize-none"
              rows={2}
              disabled={disabled}
            />
          </div>
        ))}
      </div>
    </div>
  );
}

export default SubtitleEditor;
