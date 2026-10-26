export type TaskStage =
  | "downloading"
  | "extracting_audio"
  | "recognizing"
  | "translating"
  | "generating_subtitle"
  | "burning_subtitle"
  | "completed"
  | "failed";

/**
 * 任务摘要：列表接口返回。
 *
 * 后端不再下发 video_path / srt_path 等服务器路径，只给 has_* 布尔；
 * 也不下发全量字幕（列表 10 秒轮询会拉几十 MB）。
 */
export interface TaskSummary {
  task_id: string;
  stage: TaskStage;
  progress: number;
  message: string;
  created_at?: number;
  url?: string;
  target_language?: string;
  translate_subtitles?: boolean;
  has_video: boolean;
  has_burned_video: boolean;
  has_srt: boolean;
  has_recognized_text: boolean;
  duration: number;
  subtitle_count: number;
}

/** 任务详情：单任务查询返回，额外带字幕内容供编辑器使用 */
export interface TaskDetail extends Omit<TaskSummary, "subtitle_count"> {
  subtitles: SubtitleEntry[];
}

export interface SubtitleEntry {
  index: number;
  start_time: string;
  end_time: string;
  source_text: string;
  translated_text: string;
}

export interface ProcessRequest {
  url: string;
  target_language: string;
  translate_subtitles: boolean;
}
