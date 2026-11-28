'use client';

import { authRepository } from '@/infrastructure/repositories/auth.repository';
import { GoogleLogin } from '@react-oauth/google';

export function GoogleLoginButton() {
    return (
        <GoogleLogin
            text="continue_with"
            theme="outline"
            size="large"
            onSuccess={async (response) => {
                if (!response.credential) return;

                try {
                    const result =
                        await authRepository.loginWithGoogle(
                            response.credential
                        );

                } catch (error) {
                }
            }}
            onError={() => {
                console.error('Google login failed');
            }}
        />
    );
}
