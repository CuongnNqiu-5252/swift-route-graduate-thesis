'use client';

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { authRepository } from '@/infrastructure/repositories/auth.repository';
import { clearSessionAndRedirect } from '@/lib/api';

export interface AuthUser {
  id: string;
  email: string;
  role: string;
  systemRole?: string;
  accountRole?: string;
  tenantRole?: string | null;
  permissions: string[];
  permissionsVersion?: number;
  name?: string;
  phone?: string;
  tenantId?: string | null;
  status?: string;
  emailVerified?: boolean;
  createdAt?: string;
}

export interface LivePermissionSyncPayload {
  membershipRole?: string;
  permissions?: string[];
  permissionsVersion?: number;
  status?: string;
}

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  login: (email: string, password: string) => Promise<AuthUser>;
  logout: () => void;
  refreshProfile: () => Promise<AuthUser | null>;
  hasPermission: (permission: string | string[]) => boolean;
  hasAllPermissions: (permissions: string[]) => boolean;
  applyLivePermissions: (payload: LivePermissionSyncPayload) => void;
  isLoading: boolean;
}

interface JwtClaimsPayload {
  system_role?: string;
  tenant_id?: string | null;
  tenant_role?: string | null;
  permissions?: string[];
  perm_ver?: number;
}

function normalizePermissions(raw?: unknown): string[] {
  if (!Array.isArray(raw)) return [];
  return Array.from(
    new Set(
      raw
        .filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
        .map((item) => item.trim().toLowerCase())
    )
  );
}

function decodeJwtClaims(jwt: string | null): JwtClaimsPayload {
  if (!jwt) return {};
  try {
    const parts = jwt.split('.');
    if (parts.length < 2) return {};
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const json = decodeURIComponent(
      atob(padded)
        .split('')
        .map((c) => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .join('')
    );
    return JSON.parse(json) as JwtClaimsPayload;
  } catch {
    return {};
  }
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Fetch official user profile directly from GET /api/auth/me
  const refreshProfile = useCallback(async (): Promise<AuthUser | null> => {
    try {
      const data = await authRepository.getProfile();
      if (!data) return null;

      const storedToken = localStorage.getItem('access_token');
      const claims = decodeJwtClaims(storedToken);
      const resolvedTenantId =
        claims.tenant_id ?? localStorage.getItem('tenant_id') ?? data.tenant_id ?? data.tenantId ?? null;
      if (resolvedTenantId && !localStorage.getItem('tenant_id')) {
        localStorage.setItem('tenant_id', resolvedTenantId);
      }
      const systemRole = (claims.system_role || data.account_role || data.accountRole)?.toUpperCase();
      const tenantRole = (
        data.role && data.role !== systemRole
          ? data.role
          : (claims.tenant_role ?? null)
      )?.toUpperCase() ?? null;
      const effectivePerms = normalizePermissions(data.permissions ?? claims.permissions);

      const profile: AuthUser = {
        id: data.id,
        email: data.email,
        role: tenantRole || data.role || systemRole || '',
        systemRole,
        accountRole: systemRole,
        tenantRole,
        permissions: effectivePerms,
        permissionsVersion: typeof claims.perm_ver === 'number' ? claims.perm_ver : undefined,
        name: data.full_name || data.fullName || data.email,
        phone: data.phone,
        tenantId: resolvedTenantId,
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
            const parsed = JSON.parse(storedUser) as Partial<AuthUser>;
            setUser({
              ...(parsed as AuthUser),
              permissions: normalizePermissions(parsed.permissions),
            });
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

  const applyLivePermissions = useCallback((payload: LivePermissionSyncPayload) => {
    setUser((current) => {
      if (!current) return current;
      const nextTenantRole = payload.membershipRole
        ? payload.membershipRole.toUpperCase()
        : current.tenantRole;
      const nextPermissions = payload.permissions
        ? normalizePermissions(payload.permissions)
        : current.permissions;
      const updated: AuthUser = {
        ...current,
        role: nextTenantRole || current.role,
        tenantRole: nextTenantRole,
        permissions: nextPermissions,
        permissionsVersion: payload.permissionsVersion ?? current.permissionsVersion,
        status: payload.status ?? current.status,
      };
      if (typeof window !== 'undefined') {
        localStorage.setItem('auth_user', JSON.stringify(updated));
      }
      return updated;
    });
  }, []);

  const hasPermission = useCallback(
    (permission: string | string[]): boolean => {
      if (!user) return false;
      const systemRole = (user.systemRole ?? user.accountRole ?? '').toUpperCase();
      const tenantRole = (user.tenantRole ?? user.role ?? '').toUpperCase();
      if (systemRole === 'SYSTEM_ADMIN' || tenantRole === 'OWNER') {
        return true;
      }
      const userPerms = new Set(normalizePermissions(user.permissions));
      const required = Array.isArray(permission) ? permission : [permission];
      return required.some((code) => userPerms.has(code.trim().toLowerCase()));
    },
    [user]
  );

  const hasAllPermissions = useCallback(
    (permissions: string[]): boolean => {
      if (!user) return false;
      const systemRole = (user.systemRole ?? user.accountRole ?? '').toUpperCase();
      const tenantRole = (user.tenantRole ?? user.role ?? '').toUpperCase();
      if (systemRole === 'SYSTEM_ADMIN' || tenantRole === 'OWNER') {
        return true;
      }
      const userPerms = new Set(normalizePermissions(user.permissions));
      return permissions.every((code) => userPerms.has(code.trim().toLowerCase()));
    },
    [user]
  );

  const login = async (email: string, password: string) => {
    const tokens = await authRepository.login(email, password);

    const accessToken = tokens?.access_token;
    const refreshToken = tokens?.refresh_token;

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
      authRepository.logout(refreshToken).catch(() => {});
    }
    clearSessionAndRedirect();
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        login,
        logout,
        refreshProfile,
        hasPermission,
        hasAllPermissions,
        applyLivePermissions,
        isLoading,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
