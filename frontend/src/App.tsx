import { useState, useEffect } from "react";
import VideoInput from "./components/VideoInput";
import ProgressPanel from "./components/ProgressPanel";
import SubtitleEditor from "./components/SubtitleEditor";
import VideoPlayer from "./components/VideoPlayer";
import DownloadPanel from "./components/DownloadPanel";
import LoginPage from "./components/LoginPage";
import useTask from "./hooks/useTask";
import useWebSocket from "./hooks/useWebSocket";
import { saveSubtitle, API_BASE_URL, login, getToken, setToken, removeToken } from "./api/client";
import type { SubtitleEntry, ProcessRequest } from "./types";

function App() {
  const [authenticated, setAuthenticated] = useState(!!getToken());
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [username, setUsername] = useState<string>("");

  const { taskId, status, loading, startTask, refreshStatus } = useTask();
  const { stage, progress, message } = useWebSocket(taskId);
  const [subtitles, setSubtitles] = useState<SubtitleEntry[]>([]);
  const [videoUrl, setVideoUrl] = useState<string | null>(null);

  useEffect(() => {
    if (stage === "completed" && taskId) {
      refreshStatus();
    }
  }, [stage, taskId, refreshStatus]);

  useEffect(() => {
    if (status?.result) {
      setSubtitles(status.result.subtitles || []);
      if (status.result.video_path) {
        setVideoUrl(`${API_BASE_URL}/videos/${taskId}/video.mp4`);
      }
    }
  }, [status, taskId]);

  const handleLogin = async (user: string, pass: string) => {
    setAuthLoading(true);
    setAuthError(null);
    try {
      const result = await login(user, pass);
      setToken(result.token);
      setUsername(result.username);
      setAuthenticated(true);
    } catch (e: unknown) {
      setAuthError(e instanceof Error ? e.message : "登录失败");
    } finally {
      setAuthLoading(false);
    }
  };

  const handleLogout = () => {
    removeToken();
    setAuthenticated(false);
    setUsername("");
  };

  const handleSubmit = async (request: ProcessRequest) => {
    setSubtitles([]);
    setVideoUrl(null);
    await startTask(request);
  };

  const handleSaveSubtitles = async (updatedSubtitles: SubtitleEntry[]) => {
    if (!taskId) return;
    try {
      await saveSubtitle(taskId, updatedSubtitles);
      setSubtitles(updatedSubtitles);
    } catch {
      void 0;
    }
  };

  if (!authenticated) {
    return <LoginPage onLogin={handleLogin} error={authError} loading={authLoading} />;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 py-4 flex items-center justify-between">
          <h1 className="text-xl font-semibold text-gray-900">
            B站视频翻译字幕生成器
          </h1>
          <div className="flex items-center gap-3">
            <span className="text-sm text-gray-500">{username}</span>
            <button
              onClick={handleLogout}
              className="text-sm text-gray-500 hover:text-red-600 transition"
            >
              退出登录
            </button>
          </div>
        </div>
      </header>
      <main className="max-w-7xl mx-auto px-4 py-6 space-y-6">
        <VideoInput onSubmit={handleSubmit} loading={loading} />
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <ProgressPanel stage={stage} progress={progress} message={message} />
          <VideoPlayer videoUrl={videoUrl} />
        </div>
        <SubtitleEditor subtitles={subtitles} onSave={handleSaveSubtitles} disabled={loading} />
        <DownloadPanel taskId={taskId} subtitles={subtitles} result={status?.result} />
      </main>
    </div>
  );
}

export default App;
