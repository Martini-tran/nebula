package com.nebula.common.ai.harness.realrun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/** 真实试跑授权所需的随机令牌、canonical 输入摘要与单向哈希。 */
final class RealRunSecurity {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectMapper canonicalMapper;

    RealRunSecurity(ObjectMapper objectMapper) {
        this.canonicalMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String inputDigest(Map<String, Object> input) {
        try {
            byte[] canonical = canonicalMapper.writeValueAsBytes(input == null ? Map.of() : input);
            return sha256(canonical);
        } catch (Exception e) {
            throw new IllegalArgumentException("initialInput 不是可序列化的 JSON 对象", e);
        }
    }

    String tokenHash(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return sha256(token.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }
}
