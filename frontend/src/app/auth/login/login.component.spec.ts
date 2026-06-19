import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, Router } from '@angular/router';
import { DOCUMENT } from '@angular/common';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../shared/services/auth.service';

const mockAuthService = { login: jest.fn() };

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let router: Router;

  beforeEach(async () => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: DOCUMENT, useValue: document },
        { provide: AuthService, useValue: mockAuthService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  describe('form validation', () => {
    it('form is invalid when empty', () => {
      expect(component.form.invalid).toBe(true);
    });

    it('form is invalid with bad email', () => {
      component.form.setValue({ email: 'not-an-email', password: 'password123' });
      expect(component.form.get('email')?.invalid).toBe(true);
    });

    it('form is invalid with missing password', () => {
      component.form.setValue({ email: 'user@test.com', password: '' });
      expect(component.form.invalid).toBe(true);
    });

    it('form is valid with correct values', () => {
      component.form.setValue({ email: 'user@test.com', password: 'password123' });
      expect(component.form.valid).toBe(true);
    });
  });

  describe('onSubmit', () => {
    it('does not call login if form is invalid', () => {
      component.onSubmit();
      expect(mockAuthService.login).not.toHaveBeenCalled();
    });

    it('navigates to /auth/mfa when login returns null (MFA flow)', () => {
      mockAuthService.login.mockReturnValue(of(null));
      const navigateSpy = jest.spyOn(router, 'navigate');
      component.form.setValue({ email: 'user@test.com', password: 'password123' });

      component.onSubmit();

      expect(navigateSpy).toHaveBeenCalledWith(
        ['/auth/mfa'], { queryParams: { email: 'user@test.com' } }
      );
    });

    it('navigates to /admin when login returns ADMIN token', () => {
      mockAuthService.login.mockReturnValue(
        of({ accessToken: 'jwt', role: 'ADMIN', tokenType: 'Bearer', expiresIn: 900000 })
      );
      const navigateSpy = jest.spyOn(router, 'navigate');
      component.form.setValue({ email: 'admin@test.com', password: 'password123' });

      component.onSubmit();

      expect(navigateSpy).toHaveBeenCalledWith(['/admin']);
    });

    it('navigates to /dashboard when login returns USER token', () => {
      mockAuthService.login.mockReturnValue(
        of({ accessToken: 'jwt', role: 'USER', tokenType: 'Bearer', expiresIn: 900000 })
      );
      const navigateSpy = jest.spyOn(router, 'navigate');
      component.form.setValue({ email: 'user@test.com', password: 'password123' });

      component.onSubmit();

      expect(navigateSpy).toHaveBeenCalledWith(['/dashboard']);
    });

    it('shows error message on login failure', () => {
      mockAuthService.login.mockReturnValue(
        throwError(() => ({ error: { message: 'Invalid credentials' } }))
      );
      component.form.setValue({ email: 'user@test.com', password: 'wrongpass' });

      component.onSubmit();

      expect(component.error).toBe('Invalid credentials');
      expect(component.loading).toBe(false);
    });

    it('resets loading to false after request completes', () => {
      mockAuthService.login.mockReturnValue(of(null));
      component.form.setValue({ email: 'user@test.com', password: 'password123' });

      component.onSubmit();

      expect(component.loading).toBe(false);
    });
  });

  describe('password visibility toggle', () => {
    it('defaults showPassword to false', () => {
      expect(component.showPassword).toBe(false);
    });

    it('can be set to true', () => {
      component.showPassword = true;
      expect(component.showPassword).toBe(true);
    });
  });
});
