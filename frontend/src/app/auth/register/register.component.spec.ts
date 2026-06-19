import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, Router } from '@angular/router';
import { DOCUMENT } from '@angular/common';
import { of, throwError } from 'rxjs';
import { RegisterComponent } from './register.component';
import { AuthService } from '../../shared/services/auth.service';

const mockAuthService = { register: jest.fn() };

describe('RegisterComponent', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  let component: RegisterComponent;
  let router: Router;

  beforeEach(async () => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: DOCUMENT, useValue: document },
        { provide: AuthService, useValue: mockAuthService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  describe('form validation', () => {
    it('form is invalid when empty', () => {
      expect(component.form.invalid).toBe(true);
    });

    it('username too short fails validation', () => {
      component.form.setValue({ username: 'ab', email: 'user@test.com', password: 'password123' });
      expect(component.form.get('username')?.invalid).toBe(true);
    });

    it('username too long fails validation', () => {
      component.form.setValue({
        username: 'a'.repeat(51),
        email: 'user@test.com',
        password: 'password123'
      });
      expect(component.form.get('username')?.invalid).toBe(true);
    });

    it('invalid email fails validation', () => {
      component.form.setValue({ username: 'validuser', email: 'bad-email', password: 'password123' });
      expect(component.form.get('email')?.invalid).toBe(true);
    });

    it('password under 8 chars fails validation', () => {
      component.form.setValue({ username: 'validuser', email: 'user@test.com', password: 'short' });
      expect(component.form.get('password')?.invalid).toBe(true);
    });

    it('form is valid with all correct values', () => {
      component.form.setValue({ username: 'validuser', email: 'user@test.com', password: 'password123' });
      expect(component.form.valid).toBe(true);
    });
  });

  describe('onSubmit', () => {
    it('does not call register if form is invalid', () => {
      component.onSubmit();
      expect(mockAuthService.register).not.toHaveBeenCalled();
    });

    it('calls auth.register with form values', () => {
      mockAuthService.register.mockReturnValue(of({ userId: '1', username: 'validuser', email: 'user@test.com' }));
      component.form.setValue({ username: 'validuser', email: 'user@test.com', password: 'password123' });

      component.onSubmit();

      expect(mockAuthService.register).toHaveBeenCalledWith({
        username: 'validuser', email: 'user@test.com', password: 'password123'
      });
    });

    it('shows success message and navigates to login after 1.5s', fakeAsync(() => {
      mockAuthService.register.mockReturnValue(of({ userId: '1', username: 'validuser', email: 'user@test.com' }));
      const navigateSpy = jest.spyOn(router, 'navigate').mockResolvedValue(true);
      component.form.setValue({ username: 'validuser', email: 'user@test.com', password: 'password123' });

      component.onSubmit();

      expect(component.success).toBe('Account created! Redirecting to login…');
      expect(navigateSpy).not.toHaveBeenCalled();

      tick(1500);
      expect(navigateSpy).toHaveBeenCalledWith(['/auth/login']);
    }));

    it('shows error message on registration failure', () => {
      mockAuthService.register.mockReturnValue(
        throwError(() => ({ error: { message: 'Email already registered' } }))
      );
      component.form.setValue({ username: 'validuser', email: 'taken@test.com', password: 'password123' });

      component.onSubmit();

      expect(component.error).toBe('Email already registered');
      expect(component.loading).toBe(false);
    });

    it('uses fallback error message when no message in response', () => {
      mockAuthService.register.mockReturnValue(throwError(() => ({})));
      component.form.setValue({ username: 'validuser', email: 'user@test.com', password: 'password123' });

      component.onSubmit();

      expect(component.error).toBe('Registration failed.');
    });
  });
});
