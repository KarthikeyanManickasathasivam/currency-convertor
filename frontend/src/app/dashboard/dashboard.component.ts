import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatCardModule } from '@angular/material/card';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { interval, Subscription } from 'rxjs';
import { startWith, switchMap, debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { TransactionService } from '../shared/services/transaction.service';
import { ExchangeRateService } from '../shared/services/exchange-rate.service';
import { TransactionResponse } from '../shared/models/transaction.model';
import { AuthService } from '../shared/services/auth.service';
import { UserResponse } from '../shared/models/auth.model';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

const CURRENCIES = ['USD', 'EUR', 'GBP', 'INR', 'JPY', 'CAD', 'AUD', 'CHF', 'CNY', 'SGD'];

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatFormFieldModule, MatInputModule, MatButtonModule,
    MatTableModule, MatChipsModule, MatCardModule, MatSelectModule,
    MatProgressSpinnerModule, MatTabsModule, MatIconModule
  ],
  template: `
    <div class="min-h-screen bg-slate-50">

      <!-- ── App Header ─────────────────────────────────────────────────── -->
      <div class="bg-gradient-to-r from-indigo-700 to-blue-600 text-white px-6 py-4 shadow-lg">
        <div class="max-w-5xl mx-auto flex justify-between items-center">
          <div class="flex items-center gap-3">
            <div class="w-9 h-9 bg-white bg-opacity-20 rounded-lg flex items-center justify-center">
              <mat-icon style="font-size:20px;width:20px;height:20px;">currency_exchange</mat-icon>
            </div>
            <div>
              <h1 class="text-lg font-bold leading-tight">XChange</h1>
              <p class="text-indigo-200 text-xs">Currency Converter</p>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <div *ngIf="profile" class="hidden sm:flex items-center gap-2 bg-white bg-opacity-15 rounded-lg px-3 py-1.5">
              <mat-icon style="font-size:16px;width:16px;height:16px;" class="text-indigo-200">person</mat-icon>
              <span class="text-sm text-indigo-100">{{ profile.username }}</span>
            </div>
            <button mat-stroked-button (click)="logout()"
                    class="border-white text-white" style="border-color:rgba(255,255,255,0.5)">
              <mat-icon style="font-size:16px;width:16px;height:16px;" class="mr-1">logout</mat-icon>
              Logout
            </button>
          </div>
        </div>
      </div>

      <!-- ── Page Content ────────────────────────────────────────────────── -->
      <div class="max-w-5xl mx-auto p-6">
        <mat-tab-group animationDuration="200ms" class="bg-white rounded-xl shadow-sm overflow-hidden">

          <!-- ── Tab 1: Convert ────────────────────────────────────────── -->
          <mat-tab>
            <ng-template mat-tab-label>
              <mat-icon class="mr-2" style="font-size:18px;width:18px;height:18px;">swap_horiz</mat-icon>
              Convert
            </ng-template>
            <div class="p-6">

              <!-- Conversion form card -->
              <div class="bg-gradient-to-br from-indigo-50 to-blue-50 rounded-xl border border-indigo-100 p-6 mb-6 fade-in-up">
                <h3 class="text-base font-semibold text-gray-700 mb-4 flex items-center gap-2">
                  <mat-icon class="text-indigo-500" style="font-size:18px;width:18px;height:18px;">calculate</mat-icon>
                  Currency Converter
                </h3>
                <form [formGroup]="conversionForm" (ngSubmit)="convert()" class="flex gap-4 flex-wrap items-start">

                  <mat-form-field appearance="outline" subscriptSizing="dynamic" style="background:white;border-radius:8px;">
                    <mat-label>From</mat-label>
                    <mat-select formControlName="fromCurrency">
                      <mat-option *ngFor="let c of currencies" [value]="c">{{ c }}</mat-option>
                    </mat-select>
                  </mat-form-field>

                  <div class="flex items-center mt-3 text-gray-400">
                    <mat-icon>arrow_forward</mat-icon>
                  </div>

                  <mat-form-field appearance="outline" subscriptSizing="dynamic" style="background:white;border-radius:8px;">
                    <mat-label>To</mat-label>
                    <mat-select formControlName="toCurrency">
                      <mat-option *ngFor="let c of currencies" [value]="c">{{ c }}</mat-option>
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline" subscriptSizing="dynamic" style="background:white;border-radius:8px;">
                    <mat-label>Amount</mat-label>
                    <input matInput type="number" formControlName="amount" min="0.01">
                    <mat-error>Must be greater than 0</mat-error>
                  </mat-form-field>

                  <button mat-raised-button color="primary" type="submit"
                          [disabled]="conversionForm.invalid || converting"
                          style="height:56px;padding:0 24px;font-size:14px;font-weight:500;margin-top:4px;">
                    <mat-spinner *ngIf="converting" diameter="18" class="inline-block mr-1"></mat-spinner>
                    <mat-icon *ngIf="!converting" class="mr-1" style="font-size:18px;width:18px;height:18px;">bolt</mat-icon>
                    {{ converting ? 'Converting…' : 'Convert' }}
                  </button>
                </form>

                <!-- Live rate pill -->
                <div class="mt-4 flex items-center gap-2">
                  <ng-container *ngIf="liveRate !== null && !loadingRate">
                    <div class="inline-flex items-center gap-2 bg-white border border-indigo-200 rounded-full px-3 py-1.5 text-sm text-indigo-700 shadow-sm fade-in">
                      <span class="w-2 h-2 bg-green-400 rounded-full"></span>
                      <span>Live rate: 1 {{ conversionForm.value.fromCurrency }} =
                        <strong>{{ liveRate }}</strong> {{ conversionForm.value.toCurrency }}</span>
                    </div>
                  </ng-container>
                  <mat-spinner *ngIf="loadingRate" diameter="16"></mat-spinner>
                  <span *ngIf="liveRate === null && rateNotFound && !loadingRate"
                        class="text-xs text-gray-400 italic">Rate not available for this pair</span>
                </div>
              </div>

              <!-- Conversion result -->
              <div *ngIf="lastResult" class="rounded-xl border p-5 fade-in-up"
                   [class]="lastResult.status === 'PENDING_APPROVAL'
                     ? 'bg-amber-50 border-amber-200'
                     : 'bg-emerald-50 border-emerald-200'">
                <div class="flex items-center gap-2 mb-3">
                  <mat-icon [class]="lastResult.status === 'PENDING_APPROVAL' ? 'text-amber-500' : 'text-emerald-600'"
                            style="font-size:20px;width:20px;height:20px;">
                    {{ lastResult.status === 'PENDING_APPROVAL' ? 'schedule' : 'check_circle' }}
                  </mat-icon>
                  <span class="font-semibold"
                        [class]="lastResult.status === 'PENDING_APPROVAL' ? 'text-amber-800' : 'text-emerald-800'">
                    {{ lastResult.status === 'PENDING_APPROVAL' ? 'Pending Admin Approval' : 'Conversion Successful' }}
                  </span>
                </div>
                <p class="text-2xl font-bold text-gray-800">
                  {{ lastResult.amount | number:'1.2-2' }} {{ lastResult.fromCurrency }}
                  <span class="text-gray-400 font-normal mx-2">=</span>
                  <span class="text-indigo-700">{{ lastResult.convertedAmount | number:'1.2-4' }} {{ lastResult.toCurrency }}</span>
                </p>
                <p class="text-sm text-gray-500 mt-2">
                  Rate: {{ lastResult.rate }} &nbsp;·&nbsp;
                  <span [class]="statusClass(lastResult.status) + ' px-2 py-0.5 rounded text-xs font-medium'">{{ lastResult.status }}</span>
                </p>
                <p *ngIf="lastResult.status === 'PENDING_APPROVAL'" class="text-xs text-amber-700 mt-2 flex items-center gap-1">
                  <mat-icon style="font-size:14px;width:14px;height:14px;">info</mat-icon>
                  Transactions ≥ {{ lastResult.approvalThreshold | currency:'USD':'symbol':'1.0-0' }} require admin approval before processing.
                </p>
              </div>

              <p *ngIf="conversionError" class="mt-3 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm flex items-center gap-2">
                <mat-icon style="font-size:16px;width:16px;height:16px;">error_outline</mat-icon>
                {{ conversionError }}
              </p>
            </div>
          </mat-tab>

          <!-- ── Tab 2: Transaction History ────────────────────────────── -->
          <mat-tab>
            <ng-template mat-tab-label>
              <mat-icon class="mr-2" style="font-size:18px;width:18px;height:18px;">receipt_long</mat-icon>
              History
            </ng-template>
            <div class="p-6">
              <div class="flex justify-between items-center mb-4">
                <h3 class="text-base font-semibold text-gray-700">Transaction History</h3>
                <span class="text-xs text-gray-400 flex items-center gap-1">
                  <span class="w-2 h-2 bg-green-400 rounded-full inline-block"></span>
                  Auto-refreshes every 5 s
                </span>
              </div>
              <div class="overflow-hidden rounded-lg border border-gray-100">
                <table mat-table [dataSource]="transactions" class="w-full">
                  <ng-container matColumnDef="date">
                    <th mat-header-cell *matHeaderCellDef>Date</th>
                    <td mat-cell *matCellDef="let t" class="text-sm text-gray-600">{{ t.transactionDate | date:'medium' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="pair">
                    <th mat-header-cell *matHeaderCellDef>Pair</th>
                    <td mat-cell *matCellDef="let t">
                      <span class="font-mono text-sm font-medium text-gray-800">{{ t.fromCurrency }}</span>
                      <mat-icon class="text-gray-300 align-middle mx-1" style="font-size:14px;width:14px;height:14px;">arrow_forward</mat-icon>
                      <span class="font-mono text-sm font-medium text-gray-800">{{ t.toCurrency }}</span>
                    </td>
                  </ng-container>
                  <ng-container matColumnDef="amount">
                    <th mat-header-cell *matHeaderCellDef>Amount</th>
                    <td mat-cell *matCellDef="let t" class="text-sm">{{ t.amount | number:'1.2-2' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="converted">
                    <th mat-header-cell *matHeaderCellDef>Converted</th>
                    <td mat-cell *matCellDef="let t" class="text-sm font-medium text-indigo-700">{{ t.convertedAmount | number:'1.2-4' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="status">
                    <th mat-header-cell *matHeaderCellDef>Status</th>
                    <td mat-cell *matCellDef="let t">
                      <span [class]="statusClass(t.status) + ' px-2.5 py-1 rounded-full text-xs font-semibold'">{{ t.status }}</span>
                    </td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
                  <tr mat-row *matRowDef="let row; columns: displayedColumns;" class="cursor-default"></tr>
                </table>
              </div>
              <div *ngIf="transactions.length === 0" class="py-16 text-center">
                <mat-icon class="text-gray-300" style="font-size:48px;width:48px;height:48px;">receipt_long</mat-icon>
                <p class="text-gray-500 mt-3">No transactions yet. Try converting some currency!</p>
              </div>
            </div>
          </mat-tab>

          <!-- ── Tab 3: My Profile ──────────────────────────────────────── -->
          <mat-tab>
            <ng-template mat-tab-label>
              <mat-icon class="mr-2" style="font-size:18px;width:18px;height:18px;">account_circle</mat-icon>
              Profile
            </ng-template>
            <div class="p-6 max-w-lg">

              <!-- Info card -->
              <div *ngIf="profile" class="bg-gradient-to-br from-slate-50 to-indigo-50 rounded-xl border border-slate-200 p-5 mb-6 fade-in-up">
                <div class="flex items-center gap-4 mb-4">
                  <div class="w-12 h-12 bg-indigo-600 rounded-full flex items-center justify-center text-white text-xl font-bold">
                    {{ profile.username.charAt(0).toUpperCase() }}
                  </div>
                  <div>
                    <p class="font-semibold text-gray-900">{{ profile.username }}</p>
                    <p class="text-gray-500 text-sm">{{ profile.email }}</p>
                  </div>
                  <span class="ml-auto px-3 py-1 rounded-full text-xs font-semibold"
                        [class]="profile.role === 'ADMIN' ? 'bg-purple-100 text-purple-700' : 'bg-indigo-100 text-indigo-700'">
                    {{ profile.role }}
                  </span>
                </div>
                <div class="border-t border-slate-200 pt-4 grid grid-cols-2 gap-3 text-sm">
                  <div>
                    <p class="text-gray-400 text-xs uppercase tracking-wide">Member Since</p>
                    <p class="font-medium text-gray-700 mt-0.5">{{ profile.createdAt | date:'mediumDate' }}</p>
                  </div>
                  <div>
                    <p class="text-gray-400 text-xs uppercase tracking-wide">Status</p>
                    <p class="font-medium text-green-600 mt-0.5">● Active</p>
                  </div>
                </div>
              </div>

              <!-- Update form -->
              <div class="bg-white rounded-xl border border-gray-200 p-5 fade-in-up delay-1">
                <h3 class="font-semibold text-gray-800 mb-4 flex items-center gap-2">
                  <mat-icon class="text-indigo-500" style="font-size:18px;width:18px;height:18px;">edit</mat-icon>
                  Update Profile
                </h3>
                <form [formGroup]="profileForm" (ngSubmit)="updateProfile()" class="space-y-3">
                  <mat-form-field appearance="outline" subscriptSizing="dynamic" class="w-full">
                    <mat-label>New Username</mat-label>
                    <input matInput formControlName="username">
                    <mat-error>3–50 characters required</mat-error>
                  </mat-form-field>

                  <mat-form-field appearance="outline" subscriptSizing="dynamic" class="w-full">
                    <mat-label>New Password</mat-label>
                    <input matInput type="password" formControlName="password" placeholder="Leave blank to keep current">
                    <mat-hint>Minimum 8 characters</mat-hint>
                  </mat-form-field>

                  <button mat-raised-button color="primary" type="submit" [disabled]="profileForm.invalid || savingProfile">
                    <mat-icon class="mr-1" style="font-size:16px;width:16px;height:16px;">save</mat-icon>
                    {{ savingProfile ? 'Saving…' : 'Save Changes' }}
                  </button>
                  <div *ngIf="profileSuccess" class="p-3 bg-green-50 border border-green-200 rounded-lg text-green-700 text-sm flex items-center gap-2">
                    <mat-icon style="font-size:16px;width:16px;height:16px;">check_circle</mat-icon>
                    {{ profileSuccess }}
                  </div>
                  <div *ngIf="profileError" class="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">{{ profileError }}</div>
                </form>
              </div>

            </div>
          </mat-tab>

        </mat-tab-group>
      </div>
    </div>
  `
})
export class DashboardComponent implements OnInit, OnDestroy {
  conversionForm: FormGroup;
  profileForm: FormGroup;
  transactions: TransactionResponse[] = [];
  lastResult: TransactionResponse | null = null;
  conversionError = '';
  converting = false;
  currencies = CURRENCIES;
  displayedColumns = ['date', 'pair', 'amount', 'converted', 'status'];
  profile: UserResponse | null = null;
  savingProfile = false;
  profileSuccess = '';
  profileError = '';
  liveRate: number | null = null;
  loadingRate = false;
  rateNotFound = false;

  private refreshSub?: Subscription;
  private rateSub?: Subscription;

  constructor(
    private fb: FormBuilder,
    private txService: TransactionService,
    private rateService: ExchangeRateService,
    private auth: AuthService,
    private http: HttpClient
  ) {
    this.conversionForm = this.fb.group({
      fromCurrency: ['USD', Validators.required],
      toCurrency:   ['EUR', Validators.required],
      amount:       [null, [Validators.required, Validators.min(0.01)]]
    });
    this.profileForm = this.fb.group({
      username: ['', [Validators.minLength(3), Validators.maxLength(50)]],
      password: ['', Validators.minLength(8)]
    });
  }

  ngOnInit(): void {
    this.refreshSub = interval(5000).pipe(
      startWith(0),
      switchMap(() => this.txService.getMyTransactions())
    ).subscribe(page => this.transactions = page.content);

    this.loadProfile();
    this.fetchLiveRate();

    this.rateSub = this.conversionForm.valueChanges.pipe(
      debounceTime(300),
      distinctUntilChanged((a, b) => a.fromCurrency === b.fromCurrency && a.toCurrency === b.toCurrency)
    ).subscribe(() => this.fetchLiveRate());
  }

  ngOnDestroy(): void {
    this.refreshSub?.unsubscribe();
    this.rateSub?.unsubscribe();
  }

  fetchLiveRate(): void {
    const { fromCurrency, toCurrency } = this.conversionForm.value;
    if (!fromCurrency || !toCurrency || fromCurrency === toCurrency) {
      this.liveRate = fromCurrency === toCurrency ? 1 : null;
      return;
    }
    this.loadingRate = true;
    this.rateNotFound = false;
    this.rateService.getRate(fromCurrency, toCurrency).subscribe({
      next: r  => { this.liveRate = r.rate; this.loadingRate = false; },
      error: () => { this.liveRate = null; this.rateNotFound = true; this.loadingRate = false; }
    });
  }

  convert(): void {
    if (this.conversionForm.invalid) return;
    this.converting = true;
    this.conversionError = '';
    this.txService.convert(this.conversionForm.value).subscribe({
      next: result => { this.lastResult = result; this.converting = false; },
      error: err   => { this.conversionError = err.error?.message || 'Conversion failed.'; this.converting = false; }
    });
  }

  loadProfile(): void {
    this.http.get<UserResponse>(`${environment.apiUrl}/users/me`).subscribe({
      next: p => this.profile = p,
      error: () => {}
    });
  }

  updateProfile(): void {
    const { username, password } = this.profileForm.value;
    if (!username && !password) return;
    this.savingProfile = true;
    this.profileSuccess = '';
    this.profileError = '';
    const body: Record<string, string> = {};
    if (username) body['username'] = username;
    if (password) body['password'] = password;
    this.http.put<UserResponse>(`${environment.apiUrl}/users/me`, body).subscribe({
      next: updated => {
        this.profile = updated;
        this.savingProfile = false;
        this.profileSuccess = 'Profile updated successfully.';
        this.profileForm.reset();
      },
      error: err => {
        this.savingProfile = false;
        this.profileError = err.error?.message || 'Update failed.';
      }
    });
  }

  statusClass(status: string): string {
    switch (status) {
      case 'APPROVED':         return 'text-emerald-700 bg-emerald-100';
      case 'PENDING_APPROVAL': return 'text-amber-700 bg-amber-100';
      case 'REJECTED':         return 'text-red-700 bg-red-100';
      default:                 return 'text-gray-700 bg-gray-100';
    }
  }

  logout(): void { this.auth.logout(); }
}
