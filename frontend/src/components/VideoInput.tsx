<<<<<<< HEAD
import { useState } from "react";
=======
import { useState, useEffect } from "react";
>>>>>>> trae/solo-agent-DQFIa2
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
<<<<<<< HEAD
}

function VideoInput({ onSubmit, loading }: Props) {
  const [url, setUrl] = useState("");
  const [sourceLanguage, setSourceLanguage] = useState("zh");
  const [targetLanguage, setTargetLanguage] = useState("en");
  const [translateSubtitles, setTranslateSubtitles] = useState(true);

=======
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

>>>>>>> trae/solo-agent-DQFIa2
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!url.trim()) return;
    onSubmit({
      url: url.trim(),
<<<<<<< HEAD
      source_language: sourceLanguage,
      target_language: targetLanguage,
      translate_subtitles: translateSubtitles,
=======
      target_language: targetLanguage,
      translate_subtitles: authenticated && translateSubtitles,
>>>>>>> trae/solo-agent-DQFIa2
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
<<<<<<< HEAD
          <label className="block text-sm font-medium text-gray-700 mb-1">源语言</label>
          <select
            value={sourceLanguage}
            onChange={(e) => setSourceLanguage(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition"
            disabled={loading}
          >
            {LANGUAGES.map((l) => (
              <option key={l.value} value={l.value}>{l.label}</option>
            ))}
          </select>
        </div>
        <div className="flex-1">
          <label className="block text-sm font-medium text-gray-700 mb-1">目标语言</label>
=======
          <label className="block text-sm font-medium text-gray-700 mb-1">翻译为目标语言</label>
>>>>>>> trae/solo-agent-DQFIa2
          <select
            value={targetLanguage}
            onChange={(e) => setTargetLanguage(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition"
<<<<<<< HEAD
            disabled={loading || !translateSubtitles}
=======
            disabled={loading || !authenticated || !translateSubtitles}
>>>>>>> trae/solo-agent-DQFIa2
          >
            {LANGUAGES.map((l) => (
              <option key={l.value} value={l.value}>{l.label}</option>
            ))}
          </select>
        </div>
      </div>
      <div className="flex items-center gap-3">
<<<<<<< HEAD
        <label className="relative inline-flex items-center cursor-pointer">
          <input
            type="checkbox"
            checked={translateSubtitles}
            onChange={(e) => setTranslateSubtitles(e.target.checked)}
            className="sr-only peer"
            disabled={loading}
          />
          <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full rtl:peer-checked:after:-translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:start-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
          <span className="ms-3 text-sm font-medium text-gray-700">翻译字幕</span>
        </label>
        <span className="text-xs text-gray-400">关闭则仅生成语音识别字幕</span>
=======
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
>>>>>>> trae/solo-agent-DQFIa2
      </div>
      <button
        type="submit"
        disabled={loading || !url.trim()}
        className="w-full py-2.5 px-4 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition"
      >
<<<<<<< HEAD
        {loading ? "处理中..." : "开始处理"}
=======
        {loading ? "处理中..." : (authenticated && translateSubtitles ? "开始处理" : "下载视频")}
>>>>>>> trae/solo-agent-DQFIa2
      </button>
    </form>
  );
}

export default VideoInput;
