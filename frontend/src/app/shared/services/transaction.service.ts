import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ConversionRequest,
  PageResponse,
  TransactionResponse
} from '../models/transaction.model';

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly base = `${environment.apiUrl}/transactions`;

  constructor(private http: HttpClient) {}

  convert(req: ConversionRequest): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>(this.base, req);
  }

  getMyTransactions(page = 0, size = 10): Observable<PageResponse<TransactionResponse>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'transactionDate,desc');
    return this.http.get<PageResponse<TransactionResponse>>(this.base, { params });
  }
}
