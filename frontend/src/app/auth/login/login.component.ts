import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatProgressSpinnerModule, MatIconModule
  ],
  template: `
    <div class="min-h-screen flex">

      <!-- Left: Brand panel -->
      <div class="hidden lg:flex w-1/2 bg-gradient-to-br from-indigo-800 via-indigo-700 to-blue-600
                  flex-col items-center justify-center p-12 text-white relative overflow-hidden">
        <!-- decorative circles -->
        <div class="absolute -top-24 -left-24 w-72 h-72 bg-white opacity-5 rounded-full"></div>
        <div class="absolute -bottom-16 -right-16 w-56 h-56 bg-white opacity-5 rounded-full"></div>

        <div class="relative text-center fade-in-up">
          <div class="w-20 h-20 bg-white bg-opacity-15 rounded-2xl flex items-center justify-center mb-6 mx-auto">
            <mat-icon style="font-size:40px;width:40px;height:40px;" class="text-white">currency_exchange</mat-icon>
          </div>
          <h1 class="text-4xl font-bold mb-3 tracking-tight">XChange</h1>
          <p class="text-indigo-200 text-base leading-relaxed max-w-xs mx-auto">
            Real-time currency conversion with enterprise-grade security and two-factor authentication.
          </p>
          <div class="mt-10 grid grid-cols-3 gap-4 text-center">
            <div class="bg-white bg-opacity-10 rounded-xl p-3">
              <p class="text-xl font-bold">10+</p>
              <p class="text-indigo-200 text-xs mt-1">Currencies</p>
            </div>
            <div class="bg-white bg-opacity-10 rounded-xl p-3">
              <p class="text-xl font-bold">MFA</p>
              <p class="text-indigo-200 text-xs mt-1">Secured</p>
            </div>
            <div class="bg-white bg-opacity-10 rounded-xl p-3">
              <p class="text-xl font-bold">Live</p>
              <p class="text-indigo-200 text-xs mt-1">Rates</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Form -->
      <div class="w-full lg:w-1/2 flex items-center justify-center p-8 bg-white">
        <div class="w-full max-w-md fade-in-up">
          <div class="mb-8">
            <h2 class="text-2xl font-bold text-gray-900">Welcome back</h2>
            <p class="text-gray-500 text-sm mt-1">Sign in to your account to continue</p>
          </div>

          <form [formGroup]="form" (ngSubmit)="onSubmit()">
            <mat-form-field class="w-full" appearance="outline">
              <mat-label>Email address</mat-label>
              <mat-icon matPrefix class="mr-2 text-gray-400">mail_outline</mat-icon>
              <input matInput type="email" formControlName="email" autocomplete="email">
              <mat-error *ngIf="form.get('email')?.hasError('required')">Email is required</mat-error>
              <mat-error *ngIf="form.get('email')?.hasError('email')">Invalid email address</mat-error>
            </mat-form-field>

            <mat-form-field class="w-full mt-2" appearance="outline">
              <mat-label>Password</mat-label>
              <mat-icon matPrefix class="mr-2 text-gray-400">lock_outline</mat-icon>
              <input matInput [type]="showPassword ? 'text' : 'password'"
                     formControlName="password" autocomplete="current-password">
              <button mat-icon-button matSuffix type="button" (click)="showPassword = !showPassword">
                <mat-icon class="text-gray-400">{{ showPassword ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
              <mat-error *ngIf="form.get('password')?.hasError('required')">Password is required</mat-error>
            </mat-form-field>

            <div *ngIf="error" class="mt-3 p-3 bg-red-50 border border-red-200 rounded-lg flex items-center gap-2">
              <mat-icon class="text-red-500 text-base" style="font-size:18px;width:18px;height:18px;">error_outline</mat-icon>
              <p class="text-red-700 text-sm">{{ error }}</p>
            </div>

            <button mat-raised-button color="primary" class="w-full mt-5" type="submit"
                    style="height:46px;font-size:15px;" [disabled]="form.invalid || loading">
              <mat-spinner *ngIf="loading" diameter="20" class="inline-block mr-2"></mat-spinner>
              {{ loading ? 'Signing in…' : 'Sign In' }}
            </button>
          </form>

          <p class="mt-6 text-center text-sm text-gray-500">
            Don't have an account?
            <a routerLink="/auth/register" class="text-indigo-600 font-medium hover:text-indigo-800">Register</a>
          </p>
        </div>
      </div>
    </div>
  `
})
export class LoginComponent {
  form: FormGroup;
  loading = false;
  error = '';
  showPassword = false;

  constructor(private fb: FormBuilder, private auth: AuthService, private router: Router) {
    this.form = this.fb.group({
      email:    ['', [Validators.required, Validators.email]],
      password: ['', Validators.required]
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.error = '';
    const { email, password } = this.form.value;
    this.auth.login({ email, password }).subscribe({
      next: (res) => {
        this.loading = false;
        if (res?.accessToken) {
          this.router.navigate([res.role === 'ADMIN' ? '/admin' : '/dashboard']);
        } else {
          this.router.navigate(['/auth/mfa'], { queryParams: { email } });
        }
      },
      error: (err) => {
        this.loading = false;
        this.error = err.error?.message || 'Login failed. Please try again.';
      }
    });
  }
}
