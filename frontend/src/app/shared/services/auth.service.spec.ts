import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

const mockRouter = { navigate: jest.fn() };

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    jest.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: mockRouter }
      ]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  describe('login', () => {
    it('stores token and role when response has accessToken', () => {
      const mockRes = { accessToken: 'jwt-abc', role: 'USER', tokenType: 'Bearer', expiresIn: 900000 };

      service.login({ email: 'user@test.com', password: 'pass' }).subscribe();
      httpMock.expectOne(`${environment.apiUrl}/auth/login`).flush(mockRes);

      expect(localStorage.getItem('access_token')).toBe('jwt-abc');
      expect(localStorage.getItem('user_role')).toBe('USER');
      expect(service.isLoggedIn()).toBe(true);
      expect(service.currentRole()).toBe('USER');
    });

    it('does not store token when response is null (MFA flow)', () => {
      service.login({ email: 'user@test.com', password: 'pass' }).subscribe();
      httpMock.expectOne(`${environment.apiUrl}/auth/login`).flush(null);

      expect(localStorage.getItem('access_token')).toBeNull();
      expect(service.isLoggedIn()).toBe(false);
    });
  });

  describe('verifyMfa', () => {
    it('stores token, role and updates signals', () => {
      const mockRes = { accessToken: 'mfa-jwt', role: 'ADMIN', tokenType: 'Bearer', expiresIn: 900000 };

      service.verifyMfa({ email: 'admin@test.com', otp: '123456' }).subscribe();
      httpMock.expectOne(`${environment.apiUrl}/auth/mfa/verify`).flush(mockRes);

      expect(localStorage.getItem('access_token')).toBe('mfa-jwt');
      expect(localStorage.getItem('user_role')).toBe('ADMIN');
      expect(service.isLoggedIn()).toBe(true);
      expect(service.currentRole()).toBe('ADMIN');
    });
  });

  describe('isAdmin', () => {
    it('returns true when role is ADMIN', () => {
      localStorage.setItem('user_role', 'ADMIN');
      expect(service.isAdmin()).toBe(true);
    });

    it('returns false when role is USER', () => {
      localStorage.setItem('user_role', 'USER');
      expect(service.isAdmin()).toBe(false);
    });

    it('returns false when no role stored', () => {
      expect(service.isAdmin()).toBe(false);
    });
  });

  describe('getToken', () => {
    it('returns stored token', () => {
      localStorage.setItem('access_token', 'my-token');
      expect(service.getToken()).toBe('my-token');
    });

    it('returns null when no token', () => {
      expect(service.getToken()).toBeNull();
    });
  });

  describe('clearSession', () => {
    it('removes token and role from localStorage and navigates to login', () => {
      localStorage.setItem('access_token', 'token');
      localStorage.setItem('user_role', 'USER');

      service.clearSession();

      expect(localStorage.getItem('access_token')).toBeNull();
      expect(localStorage.getItem('user_role')).toBeNull();
      expect(service.isLoggedIn()).toBe(false);
      expect(service.currentRole()).toBeNull();
      expect(mockRouter.navigate).toHaveBeenCalledWith(['/auth/login']);
    });
  });
});
