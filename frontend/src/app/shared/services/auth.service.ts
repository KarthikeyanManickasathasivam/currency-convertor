import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse,
  LoginRequest,
  MfaVerifyRequest,
  RegisterRequest,
  UserResponse
} from '../models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY = 'access_token';
  private readonly ROLE_KEY = 'user_role';

  isLoggedIn = signal(!!this.getToken());
  currentRole = signal<string | null>(this.getRole());

  constructor(private http: HttpClient, private router: Router) {}

  register(req: RegisterRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${environment.apiUrl}/auth/register`, req);
  }

  login(req: LoginRequest): Observable<AuthResponse | null> {
    return this.http.post<AuthResponse | null>(`${environment.apiUrl}/auth/login`, req).pipe(
      tap(res => {
        if (res?.accessToken) {
          localStorage.setItem(this.TOKEN_KEY, res.accessToken);
          localStorage.setItem(this.ROLE_KEY, res.role);
          this.isLoggedIn.set(true);
          this.currentRole.set(res.role);
        }
      })
    );
  }

  verifyMfa(req: MfaVerifyRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${environment.apiUrl}/auth/mfa/verify`, req).pipe(
      tap(res => {
        localStorage.setItem(this.TOKEN_KEY, res.accessToken);
        localStorage.setItem(this.ROLE_KEY, res.role);
        this.isLoggedIn.set(true);
        this.currentRole.set(res.role);
      })
    );
  }

  logout(): void {
    this.http.post(`${environment.apiUrl}/auth/logout`, {}).subscribe({
      complete: () => this.clearSession()
    });
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getRole(): string | null {
    return localStorage.getItem(this.ROLE_KEY);
  }

  isAdmin(): boolean {
    return this.getRole() === 'ADMIN';
  }

  clearSession(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.ROLE_KEY);
    this.isLoggedIn.set(false);
    this.currentRole.set(null);
    this.router.navigate(['/auth/login']);
  }
}
