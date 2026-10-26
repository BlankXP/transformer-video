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
import {
  AuthError,
  saveSubtitle,
  API_BASE_URL,
  login,
  getToken,
  setToken,
  removeToken,
} from "./api/client";
import type { SubtitleEntry, ProcessRequest } from "./types";

interface Toast {
  text: string;
  type: "ok" | "error";
}

function App() {
  const [authenticated, setAuthenticated] = useState(!!getToken());
  const [showLogin, setShowLogin] = useState(false);
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [username, setUsername] = useState<string>("");
  const [selectedUrl, setSelectedUrl] = useState<string>("");
  const [toast, setToast] = useState<Toast | null>(null);

  const { taskId, status, loading, error, startTask, refreshStatus, selectTask, clearError } = useTask();
  const { stage, progress, message } = useWebSocket(taskId);
  const [subtitles, setSubtitles] = useState<SubtitleEntry[]>([]);
  const [videoUrl, setVideoUrl] = useState<string | null>(null);

  useEffect(() => {
    if (stage === "completed" && taskId) {
      refreshStatus();
    }
  }, [stage, taskId, refreshStatus]);

  useEffect(() => {
    if (status) {
      setSubtitles(status.subtitles || []);
      // 是否有可播放视频看 has_video：服务器路径已不再下发到浏览器
      setVideoUrl(status.has_video ? `${API_BASE_URL}/api/video/${taskId}/stream` : null);
    } else {
      setSubtitles([]);
      setVideoUrl(null);
    }
  }, [status, taskId]);

  // 任务层的错误统一冒泡为提示
  useEffect(() => {
    if (!error) return;
    setToast({ text: error, type: "error" });
    // AuthError 会清掉本地令牌，这里同步退回未登录态
    if (!getToken() && authenticated) {
      setAuthenticated(false);
    }
    clearError();
  }, [error, clearError, authenticated]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 3500);
    return () => window.clearTimeout(timer);
  }, [toast]);

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
    // 查重与创建在后端一次完成（按 URL + 是否翻译 + 目标语言），
    // 命中已有任务时复用并回填状态，避免前端先查后建的竞态
    await startTask(request);
  };

  const handleSaveSubtitles = async (updatedSubtitles: SubtitleEntry[]) => {
    if (!taskId) return;
    try {
      const result = await saveSubtitle(taskId, updatedSubtitles);
      setSubtitles(updatedSubtitles);
      setToast({ text: result.message || "字幕已保存", type: "ok" });
    } catch (e) {
      if (e instanceof AuthError) {
        setAuthenticated(false);
      }
      setToast({ text: e instanceof Error ? e.message : "保存失败", type: "error" });
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

  // 子组件操作失败统一走全局 toast（不再用原生 alert）
  const showError = (text: string) => setToast({ text, type: "error" });

  const toastNode = toast ? (
    <div
      className={`fixed top-4 left-1/2 -translate-x-1/2 z-50 px-4 py-2 rounded-lg text-sm text-white shadow-lg transition ${
        toast.type === "ok" ? "bg-green-600" : "bg-red-600"
      }`}
      role="status"
    >
      {toast.text}
    </div>
  ) : null;

  if (showLogin && !authenticated) {
    return (
      <div className="min-h-screen bg-gray-50">
        {toastNode}
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
      {toastNode}
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
        <DownloadPanel taskId={taskId} task={status} />
        <TaskHistoryPanel
          authenticated={authenticated}
          onTaskSelect={handleTaskSelect}
          onRetry={handleRetry}
          onDelete={handleDelete}
          onError={showError}
        />
      </main>
    </div>
  );
}

export default App;
