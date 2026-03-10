import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { WatchedMediaResponse, UpdateWatchedRequest } from '../../shared/models/watched.model';
import { MediaResponse, MediaType } from '../../shared/models/media.model';
import { RecommendationDto } from '../../shared/models/recommendation.model';
import { CsvImportResponse } from '../../shared/models/csv-import.model';

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

  getTrending(limit: number = 10): Observable<MediaResponse[]> {
    return this.http.get<MediaResponse[]>(`/api/media/trending?limit=${limit}`);
  }

  getRecommendations(limit: number = 5): Observable<RecommendationDto[]> {
    return this.http.get<RecommendationDto[]>(`/api/media/recommendations?limit=${limit}`);
  }

  importCsv(file: File, type: MediaType): Observable<CsvImportResponse> {
    const data = new FormData();
    data.append('file', file);
    data.append('type', type);

    return this.http.post<CsvImportResponse>('/api/media/import/csv', data);
  }
}
