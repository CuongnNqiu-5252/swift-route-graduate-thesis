import api from '@/lib/api';

export interface AuthProfile {
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
  permissions?: string[];
  permissions_version?: number;
  permissionsVersion?: number;
}

export interface InvitationPreview {
  email: string;
  tenant_id: string;
  business_name?: string;
  role: string;
  expires_at: string;
  status: string;
}

interface Registration {
  email: string;
  password: string;
  full_name: string;
  phone_number?: string;
}

export const authRepository = {
  async getProfile() {
    return (await api.get<AuthProfile>('/auth/me')).data;
  },
  async login(email: string, password: string) {
    return (await api.post<{ access_token: string; refresh_token: string; user?: AuthProfile }>(
      '/auth/login', { username: email, password },
    )).data;
  },
  async logout(refreshToken: string) {
    await api.post('/auth/logout', null, { params: { refreshToken } });
  },
  async register(payload: Registration) {
    await api.post('/auth/register', payload);
  },
  async updateProfile(payload: { full_name: string; phone_number: string }) {
    await api.put('/auth/me', payload);
  },
  async verifyEmail(token: string) {
    await api.post('/auth/email/verify', { token });
  },
  async loginWithGoogle(idToken: string) {
    return (await api.post<{ access_token: string; refresh_token: string; user?: AuthProfile }>(
      '/auth/google/login',
      { id_token: idToken },
    )).data;
  },
  async linkGoogle(idToken: string) {
    await api.post('/auth/google/link', {
      id_token: idToken,
    });
  },
  async resendVerification() {
    await api.post('/auth/email/resend');
  },
  async resendPublicVerification(email: string) {
    await api.post('/auth/email/resend-public', { email });
  },
  async requestPasswordReset(email: string) {
    await api.post('/auth/password/forgot', { email });
  },
  async resetPassword(token: string, password: string) {
    await api.post('/auth/password/reset', { token, new_password: password });
  },
  async previewInvitation(token: string) {
    return (await api.post<InvitationPreview>('/auth/invitations/preview', { token })).data;
  },
};
