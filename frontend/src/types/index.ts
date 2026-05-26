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
  request?: ProcessRequest;
  created_at?: number;
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
  recognized_text_path?: string;
  subtitles: SubtitleEntry[];
  duration: number;
  completed_steps?: string;
}

export interface ProcessRequest {
  url: string;
  source_language: string;
  target_language: string;
  translate_subtitles: boolean;
}
