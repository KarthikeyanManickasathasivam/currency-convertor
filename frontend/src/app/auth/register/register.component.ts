import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule
  ],
  template: `
    <div class="min-h-screen flex">

      <!-- Left: Brand panel -->
      <div class="hidden lg:flex w-1/2 bg-gradient-to-br from-indigo-800 via-indigo-700 to-blue-600
                  flex-col items-center justify-center p-12 text-white relative overflow-hidden">
        <div class="absolute -top-24 -left-24 w-72 h-72 bg-white opacity-5 rounded-full"></div>
        <div class="absolute -bottom-16 -right-16 w-56 h-56 bg-white opacity-5 rounded-full"></div>

        <div class="relative text-center fade-in-up">
          <div class="w-20 h-20 bg-white bg-opacity-15 rounded-2xl flex items-center justify-center mb-6 mx-auto">
            <mat-icon style="font-size:40px;width:40px;height:40px;" class="text-white">currency_exchange</mat-icon>
          </div>
          <h1 class="text-4xl font-bold mb-3 tracking-tight">XChange</h1>
          <p class="text-indigo-200 text-base leading-relaxed max-w-xs mx-auto">
            Join thousands of users who trust XChange for fast, secure international currency conversion.
          </p>
          <div class="mt-10 space-y-3 text-left max-w-xs mx-auto">
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 bg-white bg-opacity-15 rounded-full flex items-center justify-center flex-shrink-0">
                <mat-icon style="font-size:16px;width:16px;height:16px;" class="text-white">check</mat-icon>
              </div>
              <p class="text-indigo-100 text-sm">MFA-secured with email OTP</p>
            </div>
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 bg-white bg-opacity-15 rounded-full flex items-center justify-center flex-shrink-0">
                <mat-icon style="font-size:16px;width:16px;height:16px;" class="text-white">check</mat-icon>
              </div>
              <p class="text-indigo-100 text-sm">Real-time exchange rates</p>
            </div>
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 bg-white bg-opacity-15 rounded-full flex items-center justify-center flex-shrink-0">
                <mat-icon style="font-size:16px;width:16px;height:16px;" class="text-white">check</mat-icon>
              </div>
              <p class="text-indigo-100 text-sm">Full transaction history</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Form -->
      <div class="w-full lg:w-1/2 flex items-center justify-center p-8 bg-white">
        <div class="w-full max-w-md fade-in-up">
          <div class="mb-8">
            <h2 class="text-2xl font-bold text-gray-900">Create your account</h2>
            <p class="text-gray-500 text-sm mt-1">Get started in under a minute</p>
          </div>

          <form [formGroup]="form" (ngSubmit)="onSubmit()">
            <mat-form-field class="w-full" appearance="outline">
              <mat-label>Username</mat-label>
              <mat-icon matPrefix class="mr-2 text-gray-400">person_outline</mat-icon>
              <input matInput formControlName="username" autocomplete="username">
              <mat-error>Username must be 3–50 characters</mat-error>
            </mat-form-field>

            <mat-form-field class="w-full mt-2" appearance="outline">
              <mat-label>Email address</mat-label>
              <mat-icon matPrefix class="mr-2 text-gray-400">mail_outline</mat-icon>
              <input matInput type="email" formControlName="email" autocomplete="email">
              <mat-error>Valid email required</mat-error>
            </mat-form-field>

            <mat-form-field class="w-full mt-2" appearance="outline">
              <mat-label>Password</mat-label>
              <mat-icon matPrefix class="mr-2 text-gray-400">lock_outline</mat-icon>
              <input matInput [type]="showPassword ? 'text' : 'password'"
                     formControlName="password" autocomplete="new-password">
              <button mat-icon-button matSuffix type="button" (click)="showPassword = !showPassword">
                <mat-icon class="text-gray-400">{{ showPassword ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
              <mat-hint>Minimum 8 characters</mat-hint>
              <mat-error>Password must be at least 8 characters</mat-error>
            </mat-form-field>

            <div *ngIf="success" class="mt-4 p-3 bg-green-50 border border-green-200 rounded-lg flex items-center gap-2">
              <mat-icon class="text-green-600" style="font-size:18px;width:18px;height:18px;">check_circle</mat-icon>
              <p class="text-green-700 text-sm">{{ success }}</p>
            </div>
            <div *ngIf="error" class="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg flex items-center gap-2">
              <mat-icon class="text-red-500" style="font-size:18px;width:18px;height:18px;">error_outline</mat-icon>
              <p class="text-red-700 text-sm">{{ error }}</p>
            </div>

            <button mat-raised-button color="primary" class="w-full mt-5" type="submit"
                    style="height:46px;font-size:15px;" [disabled]="form.invalid || loading">
              {{ loading ? 'Creating account…' : 'Create Account' }}
            </button>
          </form>

          <p class="mt-6 text-center text-sm text-gray-500">
            Already have an account?
            <a routerLink="/auth/login" class="text-indigo-600 font-medium hover:text-indigo-800">Sign in</a>
          </p>
        </div>
      </div>
    </div>
  `
})
export class RegisterComponent {
  form: FormGroup;
  loading = false;
  error = '';
  success = '';
  showPassword = false;

  constructor(private fb: FormBuilder, private auth: AuthService, private router: Router) {
    this.form = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
      email:    ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]]
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.error = '';
    this.auth.register(this.form.value).subscribe({
      next: () => {
        this.loading = false;
        this.success = 'Account created! Redirecting to login…';
        setTimeout(() => this.router.navigate(['/auth/login']), 1500);
      },
      error: (err) => {
        this.loading = false;
        this.error = err.error?.message || 'Registration failed.';
      }
    });
  }
}
