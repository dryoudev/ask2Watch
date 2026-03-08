import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest } from '../../shared/models/auth.model';
import { Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class AuthService {
  isLoggedIn = signal(false);
  username = signal('');
  token = signal<string | null>(null);

  constructor(private http: HttpClient, private router: Router) {
    this.restoreSession();
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', request).pipe(
      tap((res) => this.setSession(res))
    );
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/register', request).pipe(
      tap((res) => this.setSession(res))
    );
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    this.isLoggedIn.set(false);
    this.username.set('');
    this.token.set(null);
    this.router.navigate(['/login']);
  }

  isAuthenticated(): boolean {
    return this.isLoggedIn();
  }

  getToken(): string | null {
    return this.token();
  }

  private setSession(res: AuthResponse): void {
    localStorage.setItem('token', res.token);
    localStorage.setItem('username', res.username);
    this.token.set(res.token);
    this.username.set(res.username);
    this.isLoggedIn.set(true);
  }

  private restoreSession(): void {
    const token = localStorage.getItem('token');
    const username = localStorage.getItem('username');
    if (token && username) {
      this.token.set(token);
      this.username.set(username);
      this.isLoggedIn.set(true);
    }
  }
}
