'use client';

import { GoogleOAuthProvider } from '@react-oauth/google';
import { ReactNode } from 'react';

const googleClientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID?.trim() || '';

export const isGoogleAuthConfigured = googleClientId.length > 0;

export function GoogleAuthProvider({
  children,
}: {
  children: ReactNode;
}) {
  if (!isGoogleAuthConfigured) {
    if (process.env.NODE_ENV !== 'production') {
      console.warn('[GoogleAuthProvider] NEXT_PUBLIC_GOOGLE_CLIENT_ID is not configured.');
    }
    return <>{children}</>;
  }

  return (
    <GoogleOAuthProvider clientId={googleClientId}>
      {children}
    </GoogleOAuthProvider>
  );
}
