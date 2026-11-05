'use client';

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import api, { clearSessionAndRedirect } from '@/lib/api';

export interface AuthUser {
  id: string;
  email: string;
  role: string;
  accountRole?: string;
  name?: string;
  phone?: string;
  tenantId?: string | null;
  status?: string;
  emailVerified?: boolean;
  createdAt?: string;
}

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  login: (email: string, password: string) => Promise<AuthUser>;
  logout: () => void;
  refreshProfile: () => Promise<AuthUser | null>;
  isLoading: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Fetch official user profile directly from GET /api/auth/me
  const refreshProfile = useCallback(async (): Promise<AuthUser | null> => {
    try {
      const response = await api.get<{
        id: string;
        email: string;
        phone?: string;
        full_name?: string;
        fullName?: string;
        role: string;
        account_role?: string;
        accountRole?: string;
        tenant_id?: string | null;
        tenantId?: string | null;
        status?: string;
        email_verified?: boolean;
        emailVerified?: boolean;
        created_at?: string;
        createdAt?: string;
      }>('/auth/me');

      const data = response.data;
      if (!data) return null;

      const profile: AuthUser = {
        id: data.id,
        email: data.email,
        role: data.role,
        accountRole: data.account_role || data.accountRole,
        name: data.full_name || data.fullName || data.email,
        phone: data.phone,
        tenantId: localStorage.getItem('tenant_id') ?? data.tenant_id ?? data.tenantId ?? null,
        emailVerified: data.email_verified ?? data.emailVerified ?? false,
        status: data.status,
        createdAt: data.created_at || data.createdAt,
      };

      setUser(profile);
      localStorage.setItem('auth_user', JSON.stringify(profile));
      return profile;
    } catch (err) {
      console.warn('[AuthContext] Profile sync deferred or failed:', err);
      return null;
    }
  }, []);

  useEffect(() => {
    let isCancelled = false;

    async function initSession() {
      try {
        const storedToken = localStorage.getItem('access_token');
        const storedUser = localStorage.getItem('auth_user');

        if (!storedToken) {
          if (!isCancelled) {
            setToken(null);
            setUser(null);
            setIsLoading(false);
          }
          return;
        }

        if (storedUser) {
          try {
            setUser(JSON.parse(storedUser));
          } catch {
            localStorage.removeItem('auth_user');
          }
        }
        setToken(storedToken);

        // Fetch fresh profile from backend.
        // If storedToken is expired, the Axios interceptor in api.ts will
        // automatically refresh the token via /api/auth/refresh and retry this call.
        await refreshProfile();
      } catch (err) {
        console.warn('[AuthProvider] Failed to initialize session:', err);
      } finally {
        if (!isCancelled) {
          setIsLoading(false);
        }
      }
    }

    initSession();

    // Event listener for token refreshed via Axios interceptor
    const handleTokenRefreshed = (e: Event) => {
      const customEvent = e as CustomEvent<{ token: string }>;
      if (customEvent.detail?.token) {
        setToken(customEvent.detail.token);
      }
    };

    // Event listener for session expired / logout
    const handleLogout = () => {
      setToken(null);
      setUser(null);
    };

    window.addEventListener('auth:token-refreshed', handleTokenRefreshed);
    window.addEventListener('auth:logout', handleLogout);

    return () => {
      isCancelled = true;
      window.removeEventListener('auth:token-refreshed', handleTokenRefreshed);
      window.removeEventListener('auth:logout', handleLogout);
    };
  }, [refreshProfile]);

  const login = async (email: string, password: string) => {
    const response = await api.post<{ access_token: string; refresh_token: string }>(
      '/auth/login',
      { username: email, password }
    );

    const accessToken = response.data?.access_token;
    const refreshToken = response.data?.refresh_token;

    if (!accessToken) {
      throw new Error('Không nhận được mã xác thực từ máy chủ');
    }

    setUser(null);
    localStorage.removeItem('tenant_id');
    localStorage.removeItem('auth_user');
    localStorage.removeItem('refresh_token');
    localStorage.setItem('access_token', accessToken);
    if (refreshToken) {
      localStorage.setItem('refresh_token', refreshToken);
    }
    setToken(accessToken);

    // Fetch official user profile directly from /api/auth/me
    const profile = await refreshProfile();
    if (!profile) {
      throw new Error('Không thể tải thông tin tài khoản sau khi đăng nhập');
    }
    return profile;
  };

  const logout = () => {
    const refreshToken = typeof window !== 'undefined' ? localStorage.getItem('refresh_token') : null;
    if (refreshToken) {
      api.post('/auth/logout', null, { params: { refreshToken } }).catch(() => {});
    }
    clearSessionAndRedirect();
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, login, logout, refreshProfile, isLoading }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
