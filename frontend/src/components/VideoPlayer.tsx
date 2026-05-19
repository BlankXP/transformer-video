interface Props {
  videoUrl: string | null;
}

function VideoPlayer({ videoUrl }: Props) {
  if (!videoUrl) {
    return (
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
        <div className="aspect-video bg-gray-100 rounded-lg flex items-center justify-center">
          <p className="text-gray-400">等待视频...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-4">
      <video
        src={videoUrl}
        controls
        className="w-full rounded-lg"
      />
    </div>
  );
}

export default VideoPlayer;
