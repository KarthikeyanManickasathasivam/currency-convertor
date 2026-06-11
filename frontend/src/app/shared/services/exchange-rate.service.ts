import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RateResponse } from '../models/transaction.model';

@Injectable({ providedIn: 'root' })
export class ExchangeRateService {
  private readonly base = `${environment.apiUrl}/rates`;

  constructor(private http: HttpClient) {}

  getRate(from: string, to: string): Observable<RateResponse> {
    return this.http.get<RateResponse>(`${this.base}/${from}/${to}`);
  }

  getAllRates(): Observable<RateResponse[]> {
    return this.http.get<RateResponse[]>(this.base);
  }
}
