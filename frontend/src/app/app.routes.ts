import { Routes } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './shared/services/auth.service';
import { Router } from '@angular/router';

const authGuard = () => {
  const auth = inject(AuthService);
  if (auth.isLoggedIn()) return true;
  inject(Router).navigate(['/auth/login']);
  return false;
};

const adminGuard = () => {
  const auth = inject(AuthService);
  if (auth.isLoggedIn() && auth.isAdmin()) return true;
  inject(Router).navigate([auth.isLoggedIn() ? '/dashboard' : '/auth/login']);
  return false;
};

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  {
    path: 'auth',
    children: [
      {
        path: 'login',
        loadComponent: () => import('./auth/login/login.component').then(m => m.LoginComponent)
      },
      {
        path: 'register',
        loadComponent: () => import('./auth/register/register.component').then(m => m.RegisterComponent)
      },
      {
        path: 'mfa',
        loadComponent: () => import('./auth/mfa/mfa.component').then(m => m.MfaComponent)
      }
    ]
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  {
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () => import('./admin/admin.component').then(m => m.AdminComponent)
  },
  { path: '**', redirectTo: '/dashboard' }
];
