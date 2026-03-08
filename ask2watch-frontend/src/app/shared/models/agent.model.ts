import { MediaResponse } from './media.model';

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatResponse {
  message: string;
  suggestedMedia: MediaResponse[] | null;
}
