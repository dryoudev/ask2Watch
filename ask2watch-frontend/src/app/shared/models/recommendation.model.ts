import { MediaResponse } from './media.model';

export interface RecommendationDto {
  media: MediaResponse;
  source: string; // "Dobby" | "Films Similaires" | "Hybrid"
  reason: string; // Human-readable explanation
}
