export type MediaType = 'MOVIE' | 'SERIES';

export interface MediaResponse {
  id: number;
  imdbId: string;
  tmdbId: number;
  title: string;
  mediaType: MediaType;
  year: string;
  runtimeMins: number | null;
  genres: string;
  imdbRating: number | null;
  directors: string;
  stars: string;
  synopsis: string;
  rated: string;
  posterPath: string;
  seasons: number | null;
}
