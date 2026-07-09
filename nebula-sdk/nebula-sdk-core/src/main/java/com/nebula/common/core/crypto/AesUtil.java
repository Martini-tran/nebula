package com.nebula.common.core.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 对称加解密工具。
 *
 * <p>用于敏感字段（如模型档案的 apiKey）的可逆加密存储。算法为 {@code AES/GCM/NoPadding}：
 * 每次加密生成随机 12 字节 IV，密文格式为 {@code Base64(IV ‖ ciphertext ‖ tag)}，
 * 因此同一明文多次加密结果不同。密钥由调用方传入的字符串经 SHA-256 派生为 256 位密钥，
 * 对密钥长度无要求。
 *
 * <p>纯静态工具，无 Spring 依赖；密钥的配置与注入由使用方负责。
 *
 * @author nebula
 */
public final class AesUtil {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AesUtil() {
    }

    /**
     * 加密明文。
     *
     * @param plainText 明文（为 null 时返回 null）
     * @param secret    密钥字符串（不可为空）
     * @return Base64 编码的密文（含 IV）
     */
    public static String encrypt(String plainText, String secret) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(secret), new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解密密文。
     *
     * @param cipherTextBase64 Base64 编码的密文（含 IV，为 null 时返回 null）
     * @param secret           密钥字符串（须与加密时一致）
     * @return 明文
     */
    public static String decrypt(String cipherTextBase64, String secret) {
        if (cipherTextBase64 == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherTextBase64);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文长度非法");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(secret), new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 由密钥字符串派生 256 位 AES 密钥。
     *
     * @param secret 密钥字符串
     * @return AES 密钥规格
     */
    private static SecretKeySpec deriveKey(String secret) throws Exception {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("加密密钥不能为空");
        }
        byte[] key = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, KEY_ALGORITHM);
    }
}
