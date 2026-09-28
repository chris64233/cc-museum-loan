package com.chris64233.cc.museumloan.service;

import com.chris64233.cc.museumloan.exception.ValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 幂等记录的 JSON 序列化与内容哈希工具。 */
final class JsonUtil {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private JsonUtil() {
    }

    static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new ValidationException("请求内容无法序列化: " + e.getMessage());
        }
    }

    static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
