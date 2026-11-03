package com.swiftroute.notificationservice;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VerificationCipher {
    private final SecretKeySpec key;
    public VerificationCipher(@Value("${jwt.secret}") String secret) throws NoSuchAlgorithmException {
        key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)),"AES");
    }
    public String encrypt(String text) {
        try {
            byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
            byte[] data = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(nonce)+"."+Base64.getEncoder().encodeToString(data);
        } catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    public String decrypt(String text) {
        try {
            String[] parts=text.split("\\."); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
}
