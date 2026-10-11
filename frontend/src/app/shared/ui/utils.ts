export function formatDuration(duration: string): string {
  const match = duration.match(/PT(?:(\d+)H)?(?:(\d+)M)?/);

  if (!match) {
    return duration;
  }

  const hours = Number(match[1] ?? 0);
  const minutes = Number(match[2] ?? 0);

  if (hours === 0) {
    return `${minutes} min`;
  }

  if (minutes === 0) {
    return `${hours} h`;
  }

  return `${hours} h ${minutes.toString().padStart(2, '0')} min`;
}
