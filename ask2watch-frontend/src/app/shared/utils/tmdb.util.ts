const TMDB_IMAGE_BASE = 'https://image.tmdb.org/t/p';

export function posterUrl(posterPath: string | null, size = 'w500'): string {
  if (!posterPath) {
    return 'assets/no-poster.svg';
  }
  return `${TMDB_IMAGE_BASE}/${size}${posterPath}`;
}
