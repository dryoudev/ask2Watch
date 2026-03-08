import { MediaResponse } from './media.model';

export interface WatchedMediaResponse {
  watchedId: number;
  media: MediaResponse;
  userRating: number | null;
  dateWatched: string | null;
  comment: string | null;
}

export interface UpdateWatchedRequest {
  userRating?: number;
  comment?: string;
}
