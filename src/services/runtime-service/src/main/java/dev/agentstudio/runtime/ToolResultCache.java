package dev.agentstudio.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Tenant-isolated, fail-open cache for deterministic read-only tool results. */
@Component
class ToolResultCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;

    ToolResultCache(StringRedisTemplate redis, ObjectMapper json,
            @Value("${runtime.tool-cache.ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.json = json;
        this.ttl = Duration.ofSeconds(Math.max(1, ttlSeconds));
    }

    Optional<Object> get(String tenantId, String capability, Map<String,Object> input) {
        try {
            String value = redis.opsForValue().get(key(tenantId, capability, input));
            return value == null ? Optional.empty() : Optional.of(json.readValue(value, Object.class));
        } catch (Exception unavailable) {
            return Optional.empty();
        }
    }

    void put(String tenantId, String capability, Map<String,Object> input, Object output) {
        try {
            redis.opsForValue().set(key(tenantId, capability, input), json.writeValueAsString(output), ttl);
        } catch (Exception ignored) {
            // Redis is an optimization. Tool execution remains available when the cache is down.
        }
    }

    private String key(String tenantId, String capability, Map<String,Object> input) throws Exception {
        String material = tenantId + "\n" + capability + "\n" + json.writeValueAsString(input);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8)));
        return "tool-result:" + tenantId + ":" + capability + ":" + digest;
    }
}
