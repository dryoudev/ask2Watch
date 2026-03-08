import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PickResponse } from '../../shared/models/pick.model';

@Injectable({ providedIn: 'root' })
export class PickService {

  constructor(private http: HttpClient) {}

  getCurrentPicks(): Observable<PickResponse[]> {
    return this.http.get<PickResponse[]>('/api/picks/current');
  }

  getAllPicks(): Observable<PickResponse[]> {
    return this.http.get<PickResponse[]>('/api/picks');
  }
}
