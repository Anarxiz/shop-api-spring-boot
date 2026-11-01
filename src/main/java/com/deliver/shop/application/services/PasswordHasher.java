package com.deliver.shop.application.services;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** PBKDF2-HMAC-SHA256, 100 000 итераций, соль 16 байт, хеш 32 байта. */
@Component
public class PasswordHasher {

    public record HashedPassword(String hash, String salt) {
    }

    private static final int SALT_SIZE = 16;
    private static final int HASH_BITS = 256;
    private static final int ITERATIONS = 100_000;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private final SecureRandom random = new SecureRandom();

    public HashedPassword hash(String password) {
        byte[] salt = new byte[SALT_SIZE];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt);
        return new HashedPassword(
                Base64.getEncoder().encodeToString(hash),
                Base64.getEncoder().encodeToString(salt));
    }

    public boolean verify(String password, String hash, String salt) {
        byte[] expected = Base64.getDecoder().decode(hash);
        byte[] actual = derive(password, Base64.getDecoder().decode(salt));
        return MessageDigest.isEqual(expected, actual); // сравнение за постоянное время
    }

    private static byte[] derive(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, HASH_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось вычислить хеш пароля", e);
        }
    }
}
