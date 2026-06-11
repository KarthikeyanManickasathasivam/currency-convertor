import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { AdminService, DashboardStats } from '../shared/services/admin.service';
import { AuthService } from '../shared/services/auth.service';
import { TransactionResponse, RateResponse, LogEntry } from '../shared/models/transaction.model';
import { UserResponse } from '../shared/models/auth.model';

const CURRENCIES = ['USD', 'EUR', 'GBP', 'INR', 'JPY', 'CAD', 'AUD', 'CHF', 'CNY', 'SGD'];

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, FormsModule,
    MatTableModule, MatButtonModule, MatCardModule, MatTabsModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatIconModule, MatProgressSpinnerModule
  ],
  template: `
    <div class="min-h-screen bg-slate-50">

      <!-- ── App Header ─────────────────────────────────────────────────── -->
      <div class="bg-gradient-to-r from-indigo-700 to-blue-600 text-white px-6 py-4 shadow-lg">
        <div class="max-w-7xl mx-auto flex justify-between items-center">
          <div class="flex items-center gap-3">
            <div class="w-9 h-9 bg-white bg-opacity-20 rounded-lg flex items-center justify-center">
              <mat-icon style="font-size:20px;width:20px;height:20px;">admin_panel_settings</mat-icon>
            </div>
            <div>
              <h1 class="text-lg font-bold leading-tight">Admin Dashboard</h1>
              <p class="text-indigo-200 text-xs">XChange Management Console</p>
            </div>
          </div>
          <button mat-stroked-button (click)="logout()"
                  class="border-white text-white" style="border-color:rgba(255,255,255,0.5)">
            <mat-icon style="font-size:16px;width:16px;height:16px;" class="mr-1">logout</mat-icon>
            Logout
          </button>
        </div>
      </div>

      <div class="max-w-7xl mx-auto p-6">

        <!-- ── Stat Cards ──────────────────────────────────────────────── -->
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6" *ngIf="stats">
          <div class="stat-card border-l-blue-500 fade-in-up delay-1">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-3xl font-bold text-gray-800">{{ stats.totalUsers }}</p>
                <p class="text-gray-500 text-sm mt-1">Total Users</p>
              </div>
              <div class="w-11 h-11 bg-blue-100 rounded-xl flex items-center justify-center">
                <mat-icon class="text-blue-600">people</mat-icon>
              </div>
            </div>
          </div>

          <div class="stat-card border-l-emerald-500 fade-in-up delay-2">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-3xl font-bold text-gray-800">{{ stats.totalTransactions }}</p>
                <p class="text-gray-500 text-sm mt-1">Transactions</p>
              </div>
              <div class="w-11 h-11 bg-emerald-100 rounded-xl flex items-center justify-center">
                <mat-icon class="text-emerald-600">swap_horiz</mat-icon>
              </div>
            </div>
          </div>

          <div class="stat-card border-l-amber-500 fade-in-up delay-3">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-3xl font-bold text-gray-800">{{ stats.pendingApprovals }}</p>
                <p class="text-gray-500 text-sm mt-1">Pending Approvals</p>
              </div>
              <div class="w-11 h-11 bg-amber-100 rounded-xl flex items-center justify-center">
                <mat-icon class="text-amber-600">pending_actions</mat-icon>
              </div>
            </div>
          </div>

          <div class="stat-card border-l-purple-500 fade-in-up delay-4">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-3xl font-bold text-gray-800">{{ stats.activeUsers }}</p>
                <p class="text-gray-500 text-sm mt-1">Active Users</p>
              </div>
              <div class="w-11 h-11 bg-purple-100 rounded-xl flex items-center justify-center">
                <mat-icon class="text-purple-600">verified_user</mat-icon>
              </div>
            </div>
          </div>
        </div>

        <!-- ── Tabs ────────────────────────────────────────────────────── -->
        <div class="bg-white rounded-xl shadow-sm overflow-hidden fade-in-up">
          <mat-tab-group animationDuration="200ms">

            <!-- Tab 1: Pending Approvals -->
            <mat-tab>
              <ng-template mat-tab-label>
                <mat-icon class="mr-1" style="font-size:16px;width:16px;height:16px;">pending_actions</mat-icon>
                Pending
                <span *ngIf="pendingTx.length > 0"
                      class="ml-2 bg-amber-500 text-white text-xs rounded-full px-1.5 py-0.5 font-bold">
                  {{ pendingTx.length }}
                </span>
              </ng-template>
              <div class="p-6">
                <div *ngIf="pendingTx.length === 0" class="py-16 text-center">
                  <mat-icon class="text-gray-300" style="font-size:48px;width:48px;height:48px;">check_circle</mat-icon>
                  <p class="text-gray-500 mt-3">No pending approvals — all caught up!</p>
                </div>
                <div *ngIf="pendingTx.length > 0" class="overflow-hidden rounded-lg border border-gray-100">
                  <table mat-table [dataSource]="pendingTx" class="w-full">
                    <ng-container matColumnDef="id">
                      <th mat-header-cell *matHeaderCellDef>Transaction ID</th>
                      <td mat-cell *matCellDef="let t" class="font-mono text-xs text-gray-500">{{ t.transactionId | slice:0:8 }}…</td>
                    </ng-container>
                    <ng-container matColumnDef="amount">
                      <th mat-header-cell *matHeaderCellDef>Conversion</th>
                      <td mat-cell *matCellDef="let t">
                        <span class="font-medium text-gray-800">{{ t.amount | number:'1.2-2' }} {{ t.fromCurrency }}</span>
                        <mat-icon class="text-gray-300 align-middle mx-1" style="font-size:14px;width:14px;height:14px;">arrow_forward</mat-icon>
                        <span class="font-medium text-indigo-700">{{ t.convertedAmount | number:'1.2-4' }} {{ t.toCurrency }}</span>
                      </td>
                    </ng-container>
                    <ng-container matColumnDef="date">
                      <th mat-header-cell *matHeaderCellDef>Date</th>
                      <td mat-cell *matCellDef="let t" class="text-sm text-gray-500">{{ t.transactionDate | date:'medium' }}</td>
                    </ng-container>
                    <ng-container matColumnDef="actions">
                      <th mat-header-cell *matHeaderCellDef>Actions</th>
                      <td mat-cell *matCellDef="let t" class="py-2">
                        <button mat-raised-button color="primary" class="mr-2"
                                style="font-size:12px;" (click)="approve(t.transactionId)">
                          <mat-icon style="font-size:14px;width:14px;height:14px;" class="mr-1">check</mat-icon>
                          Approve
                        </button>
                        <button mat-raised-button color="warn"
                                style="font-size:12px;" (click)="reject(t.transactionId)">
                          <mat-icon style="font-size:14px;width:14px;height:14px;" class="mr-1">close</mat-icon>
                          Reject
                        </button>
                      </td>
                    </ng-container>
                    <tr mat-header-row *matHeaderRowDef="['id','amount','date','actions']"></tr>
                    <tr mat-row *matRowDef="let row; columns: ['id','amount','date','actions'];"></tr>
                  </table>
                </div>
              </div>
            </mat-tab>

            <!-- Tab 2: All Transactions -->
            <mat-tab>
              <ng-template mat-tab-label>
                <mat-icon class="mr-1" style="font-size:16px;width:16px;height:16px;">receipt_long</mat-icon>
                All Transactions
              </ng-template>
              <div class="p-6">
                <div *ngIf="allTx.length === 0" class="py-16 text-center">
                  <mat-icon class="text-gray-300" style="font-size:48px;width:48px;height:48px;">receipt_long</mat-icon>
                  <p class="text-gray-500 mt-3">No transactions yet</p>
                </div>
                <div *ngIf="allTx.length > 0" class="overflow-hidden rounded-lg border border-gray-100">
                  <table mat-table [dataSource]="allTx" class="w-full">
                    <ng-container matColumnDef="id">
                      <th mat-header-cell *matHeaderCellDef>ID</th>
                      <td mat-cell *matCellDef="let t" class="font-mono text-xs text-gray-400">{{ t.transactionId | slice:0:8 }}…</td>
                    </ng-container>
                    <ng-container matColumnDef="pair">
                      <th mat-header-cell *matHeaderCellDef>Pair</th>
                      <td mat-cell *matCellDef="let t">
                        <span class="font-mono text-sm font-medium">{{ t.fromCurrency }}</span>
                        <mat-icon class="text-gray-300 align-middle mx-1" style="font-size:14px;width:14px;height:14px;">arrow_forward</mat-icon>
                        <span class="font-mono text-sm font-medium">{{ t.toCurrency }}</span>
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
                    <ng-container matColumnDef="date">
                      <th mat-header-cell *matHeaderCellDef>Date</th>
                      <td mat-cell *matCellDef="let t" class="text-sm text-gray-500">{{ t.transactionDate | date:'medium' }}</td>
                    </ng-container>
                    <tr mat-header-row *matHeaderRowDef="['id','pair','amount','converted','status','date']"></tr>
                    <tr mat-row *matRowDef="let row; columns: ['id','pair','amount','converted','status','date'];"></tr>
                  </table>
                </div>
              </div>
            </mat-tab>

            <!-- Tab 3: Rate Management -->
            <mat-tab>
              <ng-template mat-tab-label>
                <mat-icon class="mr-1" style="font-size:16px;width:16px;height:16px;">percent</mat-icon>
                Rates
              </ng-template>
              <div class="p-6">

                <!-- Add rate form -->
                <div class="bg-gradient-to-br from-slate-50 to-indigo-50 rounded-xl border border-indigo-100 p-5 mb-6">
                  <h3 class="font-semibold text-gray-700 mb-4 flex items-center gap-2">
                    <mat-icon class="text-indigo-500" style="font-size:18px;width:18px;height:18px;">add_circle</mat-icon>
                    Add Currency Pair
                  </h3>
                  <form [formGroup]="rateForm" (ngSubmit)="addRate()" class="flex gap-4 flex-wrap items-start">
                    <mat-form-field appearance="outline" style="background:white;border-radius:8px;">
                      <mat-label>From Currency</mat-label>
                      <mat-select formControlName="fromCurrency">
                        <mat-option *ngFor="let c of currencies" [value]="c">{{ c }}</mat-option>
                      </mat-select>
                      <mat-error>Required</mat-error>
                    </mat-form-field>

                    <mat-form-field appearance="outline" style="background:white;border-radius:8px;">
                      <mat-label>To Currency</mat-label>
                      <mat-select formControlName="toCurrency">
                        <mat-option *ngFor="let c of currencies" [value]="c">{{ c }}</mat-option>
                      </mat-select>
                      <mat-error>Required</mat-error>
                    </mat-form-field>

                    <mat-form-field appearance="outline" style="background:white;border-radius:8px;">
                      <mat-label>Exchange Rate</mat-label>
                      <input matInput type="number" formControlName="rate" step="0.0001" min="0.0001">
                      <mat-error>Must be > 0</mat-error>
                    </mat-form-field>

                    <button mat-raised-button color="primary" type="submit"
                            [disabled]="rateForm.invalid || savingRate"
                            style="height:56px;padding:0 20px;margin-top:4px;">
                      <mat-icon class="mr-1" style="font-size:16px;width:16px;height:16px;">add</mat-icon>
                      {{ savingRate ? 'Adding…' : 'Add Rate' }}
                    </button>
                  </form>
                  <div *ngIf="rateError" class="mt-3 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">{{ rateError }}</div>
                  <div *ngIf="rateSuccess" class="mt-3 p-3 bg-green-50 border border-green-200 rounded-lg text-green-700 text-sm flex items-center gap-2">
                    <mat-icon style="font-size:16px;width:16px;height:16px;">check_circle</mat-icon>
                    {{ rateSuccess }}
                  </div>
                </div>

                <!-- Rates table -->
                <div class="overflow-hidden rounded-lg border border-gray-100">
                  <table mat-table [dataSource]="rates" class="w-full">
                    <ng-container matColumnDef="pair">
                      <th mat-header-cell *matHeaderCellDef>Currency Pair</th>
                      <td mat-cell *matCellDef="let r">
                        <span class="font-mono font-semibold text-gray-800">{{ r.fromCurrency }}</span>
                        <span class="text-gray-400 mx-1">/</span>
                        <span class="font-mono font-semibold text-gray-800">{{ r.toCurrency }}</span>
                      </td>
                    </ng-container>
                    <ng-container matColumnDef="rate">
                      <th mat-header-cell *matHeaderCellDef>Rate</th>
                      <td mat-cell *matCellDef="let r">
                        <span *ngIf="editingRateId !== r.id" class="font-medium text-indigo-700">{{ r.rate }}</span>
                        <input *ngIf="editingRateId === r.id" type="number" [(ngModel)]="editingRateValue"
                               step="0.0001"
                               class="border border-indigo-300 rounded-lg px-3 py-1.5 w-32 text-sm
                                      focus:outline-none focus:ring-2 focus:ring-indigo-400 font-medium text-indigo-700">
                      </td>
                    </ng-container>
                    <ng-container matColumnDef="source">
                      <th mat-header-cell *matHeaderCellDef>Source</th>
                      <td mat-cell *matCellDef="let r">
                        <span class="px-2 py-0.5 rounded-full text-xs font-semibold"
                              [class]="r.source === 'MANUAL' ? 'bg-orange-100 text-orange-700' : 'bg-green-100 text-green-700'">
                          {{ r.source }}
                        </span>
                      </td>
                    </ng-container>
                    <ng-container matColumnDef="updated">
                      <th mat-header-cell *matHeaderCellDef>Last Updated</th>
                      <td mat-cell *matCellDef="let r" class="text-sm text-gray-400">{{ r.lastUpdated | date:'medium' }}</td>
                    </ng-container>
                    <ng-container matColumnDef="actions">
                      <th mat-header-cell *matHeaderCellDef>Actions</th>
                      <td mat-cell *matCellDef="let r">
                        <ng-container *ngIf="editingRateId !== r.id">
                          <button mat-icon-button color="primary" (click)="startEdit(r)" title="Edit rate">
                            <mat-icon style="font-size:18px;width:18px;height:18px;">edit</mat-icon>
                          </button>
                          <button mat-icon-button color="warn" (click)="deleteRate(r.id)" title="Delete rate">
                            <mat-icon style="font-size:18px;width:18px;height:18px;">delete_outline</mat-icon>
                          </button>
                        </ng-container>
                        <ng-container *ngIf="editingRateId === r.id">
                          <button mat-icon-button color="primary" (click)="saveEdit(r.id)" title="Save">
                            <mat-icon style="font-size:18px;width:18px;height:18px;">check</mat-icon>
                          </button>
                          <button mat-icon-button (click)="cancelEdit()" title="Cancel">
                            <mat-icon style="font-size:18px;width:18px;height:18px;">close</mat-icon>
                          </button>
                        </ng-container>
                      </td>
                    </ng-container>
                    <tr mat-header-row *matHeaderRowDef="['pair','rate','source','updated','actions']"></tr>
                    <tr mat-row *matRowDef="let row; columns: ['pair','rate','source','updated','actions'];"></tr>
                  </table>
                </div>
                <p *ngIf="rates.length === 0" class="py-8 text-center text-gray-500">No rates configured</p>
              </div>
            </mat-tab>

            <!-- Tab 4: Users -->
            <mat-tab>
              <ng-template mat-tab-label>
                <mat-icon class="mr-1" style="font-size:16px;width:16px;height:16px;">people</mat-icon>
                Users
              </ng-template>
              <div class="p-6">
                <div class="overflow-hidden rounded-lg border border-gray-100">
                  <table mat-table [dataSource]="users" class="w-full">
                    <ng-container matColumnDef="username">
                      <th mat-header-cell *matHeaderCellDef>User</th>
                      <td mat-cell *matCellDef="let u">
                        <div class="flex items-center gap-3 py-1">
                          <div class="w-8 h-8 rounded-full flex items-center justify-center text-white text-sm font-bold flex-shrink-0"
                               [style.background]="u.role === 'ADMIN' ? '#7c3aed' : '#4338ca'">
                            {{ u.username.charAt(0).toUpperCase() }}
                          </div>
                          <div>
                            <p class="font-medium text-gray-800 text-sm">{{ u.username }}</p>
                            <p class="text-gray-400 text-xs">{{ u.email }}</p>
                          </div>
                        </div>
                      </td>
                    </ng-container>
                    <ng-container matColumnDef="role">
                      <th mat-header-cell *matHeaderCellDef>Role</th>
                      <td mat-cell *matCellDef="let u">
                        <span class="px-2.5 py-1 rounded-full text-xs font-semibold"
                              [class]="u.role === 'ADMIN' ? 'bg-purple-100 text-purple-700' : 'bg-indigo-100 text-indigo-700'">
                          {{ u.role }}
                        </span>
                      </td>
                    </ng-container>
                    <ng-container matColumnDef="status">
                      <th mat-header-cell *matHeaderCellDef>Status</th>
                      <td mat-cell *matCellDef="let u">
                        <span class="flex items-center gap-1 text-sm"
                              [class]="u.isActive ? 'text-emerald-600' : 'text-gray-400'">
                          <span class="w-2 h-2 rounded-full"
                                [class]="u.isActive ? 'bg-emerald-500' : 'bg-gray-400'"></span>
                          {{ u.isActive ? 'Active' : 'Inactive' }}
                        </span>
                      </td>
                    </ng-container>
                    <ng-container matColumnDef="created">
                      <th mat-header-cell *matHeaderCellDef>Joined</th>
                      <td mat-cell *matCellDef="let u" class="text-sm text-gray-400">{{ u.createdAt | date:'mediumDate' }}</td>
                    </ng-container>
                    <ng-container matColumnDef="actions">
                      <th mat-header-cell *matHeaderCellDef>Actions</th>
                      <td mat-cell *matCellDef="let u">
                        <button mat-stroked-button color="warn" *ngIf="u.isActive"
                                style="font-size:12px;" (click)="deactivateUser(u.userId)">
                          Deactivate
                        </button>
                      </td>
                    </ng-container>
                    <tr mat-header-row *matHeaderRowDef="['username','role','status','created','actions']"></tr>
                    <tr mat-row *matRowDef="let row; columns: ['username','role','status','created','actions'];"></tr>
                  </table>
                </div>
              </div>
            </mat-tab>

            <!-- Tab 5: System Logs -->
            <mat-tab>
              <ng-template mat-tab-label>
                <mat-icon class="mr-1" style="font-size:16px;width:16px;height:16px;">article</mat-icon>
                Logs
              </ng-template>
              <div class="p-6">
                <div class="overflow-hidden rounded-lg border border-gray-100">
                  <table mat-table [dataSource]="logs" class="w-full">
                    <ng-container matColumnDef="time">
                      <th mat-header-cell *matHeaderCellDef>Time</th>
                      <td mat-cell *matCellDef="let l" class="text-sm text-gray-500">{{ l.timestamp | date:'medium' }}</td>
                    </ng-container>
                    <ng-container matColumnDef="event">
                      <th mat-header-cell *matHeaderCellDef>Event Type</th>
                      <td mat-cell *matCellDef="let l">
                        <span class="px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-700">
                          {{ l.eventType }}
                        </span>
                      </td>
                    </ng-container>
                    <ng-container matColumnDef="description">
                      <th mat-header-cell *matHeaderCellDef>Description</th>
                      <td mat-cell *matCellDef="let l" class="text-sm text-gray-700">{{ l.event }}</td>
                    </ng-container>
                    <ng-container matColumnDef="ip">
                      <th mat-header-cell *matHeaderCellDef>IP Address</th>
                      <td mat-cell *matCellDef="let l" class="font-mono text-xs text-gray-500">{{ l.ipAddress }}</td>
                    </ng-container>
                    <ng-container matColumnDef="details">
                      <th mat-header-cell *matHeaderCellDef>Details</th>
                      <td mat-cell *matCellDef="let l" class="font-mono text-xs text-gray-400">
                        {{ l.details | json }}
                      </td>
                    </ng-container>
                    <tr mat-header-row *matHeaderRowDef="['time','event','description','ip','details']"></tr>
                    <tr mat-row *matRowDef="let row; columns: ['time','event','description','ip','details'];"></tr>
                  </table>
                </div>
                <p *ngIf="logs.length === 0" class="py-8 text-center text-gray-500">No logs available</p>
              </div>
            </mat-tab>

          </mat-tab-group>
        </div>
      </div>
    </div>
  `
})
export class AdminComponent implements OnInit {
  stats: DashboardStats | null = null;
  pendingTx: TransactionResponse[] = [];
  allTx: TransactionResponse[] = [];
  users: UserResponse[] = [];
  rates: RateResponse[] = [];
  logs: LogEntry[] = [];
  currencies = CURRENCIES;

  rateForm: FormGroup;
  savingRate = false;
  rateError = '';
  rateSuccess = '';
  editingRateId: number | null = null;
  editingRateValue: number = 0;

  constructor(
    private fb: FormBuilder,
    private adminService: AdminService,
    private auth: AuthService,
    private router: Router
  ) {
    this.rateForm = this.fb.group({
      fromCurrency: ['', Validators.required],
      toCurrency:   ['', Validators.required],
      rate:         [null, [Validators.required, Validators.min(0.0001)]]
    });
  }

  ngOnInit(): void { this.loadAll(); }

  loadAll(): void {
    this.adminService.getDashboard().subscribe(s => this.stats = s);
    this.adminService.getPendingTransactions().subscribe(txs => this.pendingTx = txs);
    this.adminService.getAllTransactions().subscribe(page => this.allTx = page.content);
    this.adminService.getUsers().subscribe(page => this.users = page.content);
    this.adminService.getRates().subscribe(r => this.rates = r);
    this.adminService.getLogs().subscribe(page => this.logs = page.content);
  }

  approve(id: string): void {
    this.adminService.approveTransaction(id).subscribe(() => {
      this.adminService.getPendingTransactions().subscribe(txs => this.pendingTx = txs);
      this.adminService.getAllTransactions().subscribe(page => this.allTx = page.content);
      this.adminService.getDashboard().subscribe(s => this.stats = s);
    });
  }

  reject(id: string): void {
    const reason = prompt('Rejection reason (optional):') || '';
    this.adminService.rejectTransaction(id, reason).subscribe(() => {
      this.adminService.getPendingTransactions().subscribe(txs => this.pendingTx = txs);
      this.adminService.getAllTransactions().subscribe(page => this.allTx = page.content);
      this.adminService.getDashboard().subscribe(s => this.stats = s);
    });
  }

  addRate(): void {
    if (this.rateForm.invalid) return;
    this.savingRate = true;
    this.rateError = '';
    this.rateSuccess = '';
    this.adminService.createRate(this.rateForm.value).subscribe({
      next: () => {
        this.savingRate = false;
        this.rateSuccess = `${this.rateForm.value.fromCurrency}/${this.rateForm.value.toCurrency} rate added successfully.`;
        this.rateForm.reset();
        this.adminService.getRates().subscribe(r => this.rates = r);
      },
      error: err => { this.savingRate = false; this.rateError = err.error?.message || 'Failed to add rate.'; }
    });
  }

  startEdit(rate: RateResponse): void {
    this.editingRateId = rate.id ?? null;
    this.editingRateValue = rate.rate;
  }

  saveEdit(id: number | undefined): void {
    if (!id) return;
    this.adminService.updateRate(id, this.editingRateValue).subscribe(() => {
      this.editingRateId = null;
      this.adminService.getRates().subscribe(r => this.rates = r);
    });
  }

  cancelEdit(): void { this.editingRateId = null; }

  deleteRate(id: number | undefined): void {
    if (!id || !confirm('Delete this currency pair?')) return;
    this.adminService.deleteRate(id).subscribe(() => {
      this.adminService.getRates().subscribe(r => this.rates = r);
    });
  }

  deactivateUser(id: string): void {
    if (confirm('Deactivate this user?')) {
      this.adminService.deactivateUser(id).subscribe(() => {
        this.adminService.getUsers().subscribe(page => this.users = page.content);
        this.adminService.getDashboard().subscribe(s => this.stats = s);
      });
    }
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
