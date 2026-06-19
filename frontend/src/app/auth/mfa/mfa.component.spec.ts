import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, Router, ActivatedRoute, convertToParamMap } from '@angular/router';
import { DOCUMENT } from '@angular/common';
import { of, throwError } from 'rxjs';
import { MfaComponent } from './mfa.component';
import { AuthService } from '../../shared/services/auth.service';

const mockAuthService = { verifyMfa: jest.fn() };

const makeActivatedRoute = (email: string | null) => ({
  snapshot: { queryParamMap: convertToParamMap(email ? { email } : {}) }
});

describe('MfaComponent', () => {
  let fixture: ComponentFixture<MfaComponent>;
  let component: MfaComponent;
  let router: Router;

  const setup = async (email: string | null = 'user@test.com') => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [MfaComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: DOCUMENT, useValue: document },
        { provide: AuthService, useValue: mockAuthService },
        { provide: ActivatedRoute, useValue: makeActivatedRoute(email) }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MfaComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
  };

  describe('initialisation', () => {
    it('reads email from query params', async () => {
      await setup('user@test.com');
      fixture.detectChanges();
      expect(component.email).toBe('user@test.com');
    });

    it('redirects to login when email is missing', async () => {
      await setup(null);
      const navigateSpy = jest.spyOn(router, 'navigate');
      fixture.detectChanges();
      expect(navigateSpy).toHaveBeenCalledWith(['/auth/login']);
    });
  });

  describe('form validation', () => {
    beforeEach(async () => { await setup(); fixture.detectChanges(); });

    it('form is invalid when OTP is empty', () => {
      expect(component.form.invalid).toBe(true);
    });

    it('form is invalid for non-numeric OTP', () => {
      component.form.setValue({ otp: 'abcdef' });
      expect(component.form.get('otp')?.invalid).toBe(true);
    });

    it('form is invalid when OTP is less than 6 digits', () => {
      component.form.setValue({ otp: '12345' });
      expect(component.form.get('otp')?.invalid).toBe(true);
    });

    it('form is valid with 6-digit OTP', () => {
      component.form.setValue({ otp: '123456' });
      expect(component.form.valid).toBe(true);
    });
  });

  describe('onSubmit', () => {
    beforeEach(async () => { await setup(); fixture.detectChanges(); });

    it('does not call verifyMfa if form is invalid', () => {
      component.onSubmit();
      expect(mockAuthService.verifyMfa).not.toHaveBeenCalled();
    });

    it('calls verifyMfa with email and OTP', () => {
      mockAuthService.verifyMfa.mockReturnValue(
        of({ accessToken: 'jwt', role: 'USER', tokenType: 'Bearer', expiresIn: 900000 })
      );
      component.form.setValue({ otp: '123456' });

      component.onSubmit();

      expect(mockAuthService.verifyMfa).toHaveBeenCalledWith({ email: 'user@test.com', otp: '123456' });
    });

    it('navigates to /admin when role is ADMIN', () => {
      mockAuthService.verifyMfa.mockReturnValue(
        of({ accessToken: 'jwt', role: 'ADMIN', tokenType: 'Bearer', expiresIn: 900000 })
      );
      const navigateSpy = jest.spyOn(router, 'navigate');
      component.form.setValue({ otp: '123456' });

      component.onSubmit();

      expect(navigateSpy).toHaveBeenCalledWith(['/admin']);
    });

    it('navigates to /dashboard when role is USER', () => {
      mockAuthService.verifyMfa.mockReturnValue(
        of({ accessToken: 'jwt', role: 'USER', tokenType: 'Bearer', expiresIn: 900000 })
      );
      const navigateSpy = jest.spyOn(router, 'navigate');
      component.form.setValue({ otp: '123456' });

      component.onSubmit();

      expect(navigateSpy).toHaveBeenCalledWith(['/dashboard']);
    });

    it('shows error message on invalid OTP', () => {
      mockAuthService.verifyMfa.mockReturnValue(
        throwError(() => ({ error: { message: 'Invalid or expired OTP' } }))
      );
      component.form.setValue({ otp: '000000' });

      component.onSubmit();

      expect(component.error).toBe('Invalid or expired OTP');
      expect(component.loading).toBe(false);
    });

    it('uses fallback error message when none provided', () => {
      mockAuthService.verifyMfa.mockReturnValue(throwError(() => ({})));
      component.form.setValue({ otp: '123456' });

      component.onSubmit();

      expect(component.error).toBe('Invalid or expired OTP.');
    });
  });
});
