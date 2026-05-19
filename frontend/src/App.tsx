import { useState, useEffect } from "react";
import VideoInput from "./components/VideoInput";
import ProgressPanel from "./components/ProgressPanel";
import SubtitleEditor from "./components/SubtitleEditor";
import VideoPlayer from "./components/VideoPlayer";
import DownloadPanel from "./components/DownloadPanel";
import useTask from "./hooks/useTask";
import useWebSocket from "./hooks/useWebSocket";
import { saveSubtitle, burnVideo } from "./api/client";
import type { SubtitleEntry, ProcessRequest } from "./types";

const API_BASE_URL = "http://localhost:8000";

function App() {
  const { taskId, status, loading, startTask, refreshStatus } = useTask();
  const { stage, progress, message } = useWebSocket(taskId);
  const [subtitles, setSubtitles] = useState<SubtitleEntry[]>([]);
  const [videoUrl, setVideoUrl] = useState<string | null>(null);
  const [burning, setBurning] = useState(false);

  useEffect(() => {
    if (stage === "completed" && taskId) {
      refreshStatus();
    }
  }, [stage, taskId, refreshStatus]);

  useEffect(() => {
    if (status?.result) {
      setSubtitles(status.result.subtitles || []);
      if (status.result.video_path) {
        setVideoUrl(`${API_BASE_URL}/videos/${taskId}/output.mp4`);
      }
    }
  }, [status, taskId]);

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

  const handleBurn = async () => {
    if (!taskId) return;
    setBurning(true);
    try {
      await burnVideo(taskId);
      setVideoUrl(`${API_BASE_URL}/videos/${taskId}/output.mp4`);
    } catch {
      void 0;
    } finally {
      setBurning(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <h1 className="text-xl font-semibold text-gray-900">
            B站视频翻译字幕生成器
          </h1>
        </div>
      </header>
      <main className="max-w-7xl mx-auto px-4 py-6 space-y-6">
        <VideoInput onSubmit={handleSubmit} loading={loading} />
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <ProgressPanel stage={stage} progress={progress} message={message} />
          <VideoPlayer videoUrl={videoUrl} />
        </div>
        <SubtitleEditor subtitles={subtitles} onSave={handleSaveSubtitles} disabled={loading || burning} />
        <DownloadPanel taskId={taskId} subtitles={subtitles} onBurn={handleBurn} burning={burning} />
      </main>
    </div>
  );
}

export default App;
