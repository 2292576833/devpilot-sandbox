package com.devpilot.sandbox.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * DevPilot Sandbox Java SDK 客户端
 *
 * 用法:
 * <pre>
 * SandboxClient client = new SandboxClient("http://127.0.0.1:9091");
 * CheckResult result = client.checkFileRead("CODE_ENGINEER", "src/main/App.java");
 * if (result.isAllowed()) {
 *     System.out.println("允许访问: " + result.getResolvedPath());
 * }
 * </pre>
 */
public class SandboxClient {

    private final String baseUrl;
    private final String defaultRoleId;
    private final ObjectMapper mapper;
    private final int connectTimeout;
    private final int readTimeout;

    public SandboxClient(String baseUrl, String defaultRoleId) {
        this.baseUrl = normalizeUrl(baseUrl);
        this.defaultRoleId = defaultRoleId;
        this.mapper = new ObjectMapper();
        this.connectTimeout = 5000;
        this.readTimeout = 10000;
    }

    public SandboxClient(String baseUrl) {
        this(baseUrl, "CODE_ENGINEER");
    }

    // ========== Public API ==========

    public CheckResult checkFileRead(String roleId, String path) {
        return doGuard("/api/v1/guard/file-read", roleId, "path", path);
    }

    public CheckResult checkFileRead(String path) {
        return checkFileRead(defaultRoleId, path);
    }

    public CheckResult checkFileWrite(String roleId, String path) {
        return doGuard("/api/v1/guard/file-write", roleId, "path", path);
    }

    public CheckResult checkFileWrite(String path) {
        return checkFileWrite(defaultRoleId, path);
    }

    public CheckResult checkCommand(String roleId, String command) {
        return doGuard("/api/v1/guard/command-run", roleId, "command", command);
    }

    public CheckResult checkCommand(String command) {
        return checkCommand(defaultRoleId, command);
    }

    public HealthResult health() {
        try {
            Map<String, Object> resp = get("/api/v1/guard/health");
            return new HealthResult(
                    (String) resp.get("status"),
                    (String) resp.get("version"),
                    (Collection<String>) resp.get("roles"),
                    (String) resp.get("policySource")
            );
        } catch (Exception e) {
            return new HealthResult("DOWN", null, Collections.emptyList(), null);
        }
    }

    public List<String> queryAudit(String role, String operation, Integer limit, Integer offset) {
        try {
            Map<String, String> params = new LinkedHashMap<>();
            if (role != null) params.put("role", role);
            if (operation != null) params.put("operation", operation);
            if (limit != null) params.put("limit", limit.toString());
            if (offset != null) params.put("offset", offset.toString());

            String query = buildQuery(params);
            Map<String, Object> resp = get("/api/v1/guard/audit" + query);
            return (List<String>) resp.getOrDefault("entries", Collections.emptyList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ========== HTTP helpers ==========

    private CheckResult doGuard(String endpoint, String roleId, String key, String value) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("roleId", roleId);
            body.put(key, value);
            Map<String, Object> resp = post(endpoint, body);
            return new CheckResult(
                    (Boolean) resp.getOrDefault("allowed", false),
                    (String) resp.get("resolvedPath"),
                    (String) resp.get("reason")
            );
        } catch (Exception e) {
            return new CheckResult(false, null, "SDK error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String endpoint, Map<String, Object> body) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(mapper.writeValueAsBytes(body));
            }

            int status = conn.getResponseCode();
            InputStream stream = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("allowed", false);
                err.put("reason", "HTTP " + status + " (no body)");
                return err;
            }
            return mapper.readValue(stream, Map.class);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String endpoint) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);

            int status = conn.getResponseCode();
            InputStream stream = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) return Collections.emptyMap();
            return mapper.readValue(stream, Map.class);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String normalizeUrl(String url) {
        if (url == null) throw new IllegalArgumentException("baseUrl must not be null");
        url = url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String buildQuery(Map<String, String> params) {
        if (params.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("?");
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 1) sb.append("&");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    // ========== Result types ==========

    public static class CheckResult {
        private final boolean allowed;
        private final String resolvedPath;
        private final String reason;

        public CheckResult(boolean allowed, String resolvedPath, String reason) {
            this.allowed = allowed;
            this.resolvedPath = resolvedPath;
            this.reason = reason;
        }

        public boolean isAllowed() { return allowed; }
        public String getResolvedPath() { return resolvedPath; }
        public String getReason() { return reason; }
    }

    public static class HealthResult {
        private final String status;
        private final String version;
        private final Collection<String> roles;
        private final String policySource;

        public HealthResult(String status, String version, Collection<String> roles, String policySource) {
            this.status = status;
            this.version = version;
            this.roles = roles;
            this.policySource = policySource;
        }

        public String getStatus() { return status; }
        public String getVersion() { return version; }
        public Collection<String> getRoles() { return roles; }
        public String getPolicySource() { return policySource; }
        public boolean isUp() { return "UP".equals(status); }
    }
}
