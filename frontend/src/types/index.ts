export type TaskStage =
  | "downloading"
  | "extracting_audio"
  | "recognizing"
  | "translating"
  | "generating_subtitle"
  | "burning_subtitle"
  | "completed"
  | "failed";

export interface TaskStatus {
  task_id: string;
  stage: TaskStage;
  progress: number;
  message: string;
  result?: TaskResult;
<<<<<<< HEAD
=======
  request?: ProcessRequest;
  created_at?: number;
>>>>>>> trae/solo-agent-DQFIa2
}

export interface SubtitleEntry {
  index: number;
  start_time: string;
  end_time: string;
  source_text: string;
  translated_text: string;
}

export interface TaskResult {
  video_path: string;
  srt_path: string;
  burned_video_path?: string;
<<<<<<< HEAD
  subtitles: SubtitleEntry[];
  duration: number;
=======
  recognized_text_path?: string;
  subtitles: SubtitleEntry[];
  duration: number;
  completed_steps?: string;
>>>>>>> trae/solo-agent-DQFIa2
}

export interface ProcessRequest {
  url: string;
<<<<<<< HEAD
  source_language: string;
=======
>>>>>>> trae/solo-agent-DQFIa2
  target_language: string;
  translate_subtitles: boolean;
}
