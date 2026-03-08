import { MediaResponse } from './media.model';

export interface PickResponse {
  pickId: number;
  media: MediaResponse;
  weekDate: string;
  createdByAgent: boolean;
}
