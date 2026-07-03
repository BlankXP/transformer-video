import { useState, useEffect } from "react";
import type { ProcessRequest } from "../types";

const LANGUAGES = [
  { value: "zh", label: "中文" },
  { value: "en", label: "英文" },
  { value: "ja", label: "日文" },
  { value: "ko", label: "韩文" },
  { value: "fr", label: "法文" },
  { value: "de", label: "德文" },
  { value: "es", label: "西班牙文" },
];

interface Props {
  onSubmit: (request: ProcessRequest) => void;
  loading: boolean;
  authenticated: boolean;
  onLoginClick: () => void;
  initialUrl?: string;
}

function VideoInput({ onSubmit, loading, authenticated, onLoginClick, initialUrl }: Props) {
  const [url, setUrl] = useState("");
  const [targetLanguage, setTargetLanguage] = useState("en");
  const [translateSubtitles, setTranslateSubtitles] = useState(true);

  useEffect(() => {
    if (!authenticated) {
      setTranslateSubtitles(false);
    }
  }, [authenticated]);

  useEffect(() => {
    if (initialUrl) {
      setUrl(initialUrl);
    }
  }, [initialUrl]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!url.trim()) return;
    onSubmit({
      url: url.trim(),
      target_language: targetLanguage,
      translate_subtitles: authenticated && translateSubtitles,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 space-y-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">视频链接</label>
        <input
          type="text"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="B站链接/BV号 或 YouTube链接"
          className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition"
          disabled={loading}
        />
      </div>
      <div className="flex gap-4">
        <div className="flex-1">
          <label className="block text-sm font-medium text-gray-700 mb-1">翻译为目标语言</label>
          <select
            value={targetLanguage}
            onChange={(e) => setTargetLanguage(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition"
            disabled={loading || !authenticated || !translateSubtitles}
          >
            {LANGUAGES.map((l) => (
              <option key={l.value} value={l.value}>{l.label}</option>
            ))}
          </select>
        </div>
      </div>
      <div className="flex items-center gap-3">
        <label className={`relative inline-flex items-center ${authenticated ? 'cursor-pointer' : 'cursor-not-allowed'}`}>
          <input
            type="checkbox"
            checked={authenticated && translateSubtitles}
            onChange={(e) => {
              if (authenticated) setTranslateSubtitles(e.target.checked);
            }}
            className="sr-only peer"
            disabled={loading || !authenticated}
          />
          <div className={`w-11 h-6 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-blue-300 rounded-full peer after:content-[''] after:absolute after:top-[2px] after:start-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all ${authenticated ? 'peer-checked:bg-blue-600 peer-checked:after:translate-x-full' : 'bg-gray-200'} ${authenticated ? 'bg-gray-200 peer-checked:after:translate-x-full' : ''}`}></div>
          <span className={`ms-3 text-sm font-medium ${authenticated ? 'text-gray-700' : 'text-gray-400'}`}>翻译字幕</span>
        </label>
        {authenticated ? (
          <span className="text-xs text-gray-400">关闭则仅下载视频，不生成字幕</span>
        ) : (
          <button type="button" onClick={onLoginClick} className="text-xs text-blue-600 hover:text-blue-700 transition">
            登录后可使用翻译字幕功能
          </button>
        )}
      </div>
      <button
        type="submit"
        disabled={loading || !url.trim()}
        className="w-full py-2.5 px-4 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition"
      >
        {loading ? "处理中..." : (authenticated && translateSubtitles ? "开始处理" : "下载视频")}
      </button>
    </form>
  );
}

export default VideoInput;
