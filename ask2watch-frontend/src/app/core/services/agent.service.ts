import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChatResponse } from '../../shared/models/agent.model';
import { PickResponse } from '../../shared/models/pick.model';

@Injectable({ providedIn: 'root' })
export class AgentService {

  constructor(private http: HttpClient) {}

  chat(message: string): Observable<ChatResponse> {
    return this.http.post<ChatResponse>('/api/agent/chat', { message });
  }

  generatePicks(): Observable<PickResponse[]> {
    return this.http.post<PickResponse[]>('/api/agent/generate-picks', {});
  }

  clearHistory(): Observable<void> {
    return this.http.delete<void>('/api/agent/history');
  }
}
