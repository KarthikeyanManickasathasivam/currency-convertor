import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  LogEntry,
  PageResponse,
  RateResponse,
  TransactionResponse
} from '../models/transaction.model';
import { UserResponse } from '../models/auth.model';

export interface DashboardStats {
  totalUsers: number;
  activeUsers: number;
  totalTransactions: number;
  pendingApprovals: number;
  topFromCurrencies: string[];
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly base = `${environment.apiUrl}/admin`;

  constructor(private http: HttpClient) {}

  getDashboard(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.base}/dashboard`);
  }

  getUsers(page = 0, size = 20): Observable<PageResponse<UserResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<UserResponse>>(`${this.base}/users`, { params });
  }

  deactivateUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/users/${id}`);
  }

  getPendingTransactions(): Observable<TransactionResponse[]> {
    return this.http.get<TransactionResponse[]>(`${this.base}/transactions/pending`);
  }

  getAllTransactions(page = 0, size = 20): Observable<PageResponse<TransactionResponse>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'transactionDate,desc');
    return this.http.get<PageResponse<TransactionResponse>>(`${this.base}/transactions`, { params });
  }

  approveTransaction(id: string): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>(`${this.base}/transactions/${id}/approve`, {});
  }

  rejectTransaction(id: string, reason: string): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>(`${this.base}/transactions/${id}/reject`, { reason });
  }

  getRates(): Observable<RateResponse[]> {
    return this.http.get<RateResponse[]>(`${environment.apiUrl}/rates`);
  }

  createRate(req: { fromCurrency: string; toCurrency: string; rate: number }): Observable<RateResponse> {
    return this.http.post<RateResponse>(`${environment.apiUrl}/rates/admin`, req);
  }

  updateRate(id: number, rate: number): Observable<RateResponse> {
    return this.http.put<RateResponse>(`${environment.apiUrl}/rates/admin/${id}`, { rate });
  }

  deleteRate(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/rates/admin/${id}`);
  }

  getLogs(page = 0, size = 50): Observable<PageResponse<LogEntry>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<LogEntry>>(`${this.base}/logs`, { params });
  }
}
