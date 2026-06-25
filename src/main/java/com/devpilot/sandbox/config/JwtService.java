package com.devpilot.sandbox.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component

public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${sandbox.auth.jwt-secret:devpilot-default-secret}")
    private String jwtSecret;

    @Value("${sandbox.auth.token-expiry-hours:24}")
    private int tokenExpiryHours;

    private final ObjectMapper mapper = new ObjectMapper();

    public String createToken(String username) {
        try {
            String header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
            long exp = System.currentTimeMillis() + (tokenExpiryHours * 3600000L);
            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", username);
            payload.put("iat", System.currentTimeMillis() / 1000);
            payload.put("exp", exp / 1000);
            String payloadStr = base64UrlEncode(mapper.writeValueAsString(payload));
            String signature = hmacSha256(header + "." + payloadStr);
            return header + "." + payloadStr + "." + signature;
        } catch (Exception e) {
            log.error("Failed to create token: {}", e.getMessage());
            return null;
        }
    }

    public String validateToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            String expectedSig = hmacSha256(parts[0] + "." + parts[1]);
            if (!expectedSig.equals(parts[2])) return null;
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> payload = mapper.readValue(payloadJson, Map.class);
            long exp = ((Number) payload.get("exp")).longValue() * 1000;
            if (System.currentTimeMillis() > exp) return null;
            return (String) payload.get("sub");
        } catch (Exception e) {
            return null;
        }
    }

    private String hmacSha256(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private String base64UrlEncode(String data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }
}