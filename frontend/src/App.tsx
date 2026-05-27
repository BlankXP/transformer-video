import { useState, useEffect } from "react";
import VideoInput from "./components/VideoInput";
import ProgressPanel from "./components/ProgressPanel";
import SubtitleEditor from "./components/SubtitleEditor";
import VideoPlayer from "./components/VideoPlayer";
import DownloadPanel from "./components/DownloadPanel";
import TaskHistoryPanel from "./components/TaskHistoryPanel";
import LoginPage from "./components/LoginPage";
import useTask from "./hooks/useTask";
import useWebSocket from "./hooks/useWebSocket";
import { saveSubtitle, API_BASE_URL, login, getToken, setToken, removeToken } from "./api/client";
import type { SubtitleEntry, ProcessRequest } from "./types";

function App() {
  const [authenticated, setAuthenticated] = useState(!!getToken());
  const [showLogin, setShowLogin] = useState(false);
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [username, setUsername] = useState<string>("");
  const [selectedUrl, setSelectedUrl] = useState<string>("");

  const { taskId, status, loading, startTask, refreshStatus, selectTask } = useTask();
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
        setVideoUrl(`${API_BASE_URL}/api/video/${taskId}/stream`);
      }
    } else {
      setSubtitles([]);
      setVideoUrl(null);
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
      setShowLogin(false);
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
    setSelectedUrl(request.url);
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

  const handleTaskSelect = async (selectedTaskId: string) => {
    await selectTask(selectedTaskId);
  };

  const handleRetry = async (retryTaskId: string) => {
    await selectTask(retryTaskId);
  };

  const handleDelete = (deletedTaskId: string) => {
    if (taskId === deletedTaskId) {
      setSubtitles([]);
      setVideoUrl(null);
    }
  };

  if (showLogin && !authenticated) {
    return (
      <div className="min-h-screen bg-gray-50">
        <div className="max-w-sm mx-auto pt-20">
          <LoginPage onLogin={handleLogin} error={authError} loading={authLoading} />
          <div className="text-center mt-4">
            <button
              onClick={() => { setShowLogin(false); setAuthError(null); }}
              className="text-sm text-gray-500 hover:text-gray-700 transition"
            >
              返回
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 py-4 flex items-center justify-between">
          <h1 className="text-xl font-semibold text-gray-900">
            B站视频翻译字幕生成器
          </h1>
          <div className="flex items-center gap-3">
            {authenticated ? (
              <>
                <span className="text-sm text-gray-500">{username}</span>
                <button
                  onClick={handleLogout}
                  className="text-sm text-gray-500 hover:text-red-600 transition"
                >
                  退出登录
                </button>
              </>
            ) : (
              <button
                onClick={() => setShowLogin(true)}
                className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition"
              >
                登录
              </button>
            )}
          </div>
        </div>
      </header>
      <main className="max-w-7xl mx-auto px-4 py-6 space-y-6">
        <VideoInput onSubmit={handleSubmit} loading={loading} authenticated={authenticated} onLoginClick={() => setShowLogin(true)} initialUrl={selectedUrl} />
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <ProgressPanel stage={stage} progress={progress} message={message} />
          <VideoPlayer videoUrl={videoUrl} />
        </div>
        {authenticated && (
          <SubtitleEditor subtitles={subtitles} onSave={handleSaveSubtitles} disabled={loading} />
        )}
        <DownloadPanel taskId={taskId} subtitles={subtitles} result={status?.result} />
        <TaskHistoryPanel authenticated={authenticated} onTaskSelect={handleTaskSelect} onRetry={handleRetry} onDelete={handleDelete} />
      </main>
    </div>
  );
}

export default App;
