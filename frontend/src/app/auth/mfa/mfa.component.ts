import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-mfa',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule
  ],
  template: `
    <div class="min-h-screen flex items-center justify-center bg-slate-50 px-4">
      <div class="w-full max-w-md scale-in">

        <!-- Card -->
        <div class="bg-white rounded-2xl shadow-lg overflow-hidden">

          <!-- Top accent bar -->
          <div class="h-1.5 bg-gradient-to-r from-indigo-600 to-blue-500"></div>

          <div class="p-8">
            <!-- Icon + heading -->
            <div class="flex items-center gap-4 mb-6">
              <div class="w-12 h-12 bg-indigo-100 rounded-xl flex items-center justify-center flex-shrink-0">
                <mat-icon class="text-indigo-600" style="font-size:24px;width:24px;height:24px;">shield</mat-icon>
              </div>
              <div>
                <h2 class="text-xl font-bold text-gray-900">Two-Factor Verification</h2>
                <p class="text-gray-500 text-sm mt-0.5">Enter the code sent to your email</p>
              </div>
            </div>

            <!-- Email badge -->
            <div class="bg-indigo-50 border border-indigo-100 rounded-lg px-4 py-2.5 mb-6 flex items-center gap-2">
              <mat-icon class="text-indigo-400" style="font-size:16px;width:16px;height:16px;">mail</mat-icon>
              <span class="text-indigo-700 text-sm font-medium truncate">{{ email }}</span>
            </div>

            <form [formGroup]="form" (ngSubmit)="onSubmit()">
              <mat-form-field class="w-full" appearance="outline">
                <mat-label>6-digit OTP code</mat-label>
                <input matInput formControlName="otp" placeholder="123456"
                       maxlength="6" autocomplete="one-time-code"
                       style="font-size:22px;letter-spacing:0.3em;text-align:center;">
                <mat-error *ngIf="form.get('otp')?.hasError('required')">OTP is required</mat-error>
                <mat-error *ngIf="form.get('otp')?.hasError('pattern')">Must be 6 digits</mat-error>
              </mat-form-field>

              <div *ngIf="error" class="mt-3 p-3 bg-red-50 border border-red-200 rounded-lg flex items-center gap-2">
                <mat-icon class="text-red-500" style="font-size:18px;width:18px;height:18px;">error_outline</mat-icon>
                <p class="text-red-700 text-sm">{{ error }}</p>
              </div>

              <button mat-raised-button color="primary" class="w-full mt-5" type="submit"
                      style="height:46px;font-size:15px;" [disabled]="form.invalid || loading">
                {{ loading ? 'Verifying…' : 'Verify & Continue' }}
              </button>
            </form>

            <p class="mt-4 text-center text-xs text-gray-400">
              <mat-icon style="font-size:12px;width:12px;height:12px;" class="align-middle mr-1">info</mat-icon>
              In local dev, check the server console log for the OTP
            </p>
          </div>
        </div>

        <!-- Back link -->
        <p class="mt-4 text-center text-sm text-gray-500">
          Wrong email?
          <a routerLink="/auth/login" class="text-indigo-600 font-medium hover:text-indigo-800">Go back</a>
        </p>
      </div>
    </div>
  `
})
export class MfaComponent implements OnInit {
  form: FormGroup;
  email = '';
  loading = false;
  error = '';

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private route: ActivatedRoute,
    private router: Router
  ) {
    this.form = this.fb.group({
      otp: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
    });
  }

  ngOnInit(): void {
    this.email = this.route.snapshot.queryParamMap.get('email') || '';
    if (!this.email) this.router.navigate(['/auth/login']);
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.error = '';
    this.auth.verifyMfa({ email: this.email, otp: this.form.value.otp }).subscribe({
      next: (res) => {
        this.loading = false;
        this.router.navigate([res.role === 'ADMIN' ? '/admin' : '/dashboard']);
      },
      error: (err) => {
        this.loading = false;
        this.error = err.error?.message || 'Invalid or expired OTP.';
      }
    });
  }
}
