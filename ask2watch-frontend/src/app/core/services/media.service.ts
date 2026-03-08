import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { WatchedMediaResponse, UpdateWatchedRequest } from '../../shared/models/watched.model';
import { MediaResponse } from '../../shared/models/media.model';

@Injectable({ providedIn: 'root' })
export class MediaService {

  constructor(private http: HttpClient) {}

  getWatchedMovies(): Observable<WatchedMediaResponse[]> {
    return this.http.get<WatchedMediaResponse[]>('/api/media/watched?type=MOVIE');
  }

  getWatchedSeries(): Observable<WatchedMediaResponse[]> {
    return this.http.get<WatchedMediaResponse[]>('/api/media/watched?type=SERIES');
  }

  updateWatched(id: number, request: UpdateWatchedRequest): Observable<WatchedMediaResponse> {
    return this.http.put<WatchedMediaResponse>(`/api/media/watched/${id}`, request);
  }

  getMediaById(id: number): Observable<MediaResponse> {
    return this.http.get<MediaResponse>(`/api/media/${id}`);
  }
}
