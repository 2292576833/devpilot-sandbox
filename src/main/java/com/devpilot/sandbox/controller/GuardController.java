package com.devpilot.sandbox.controller;

import com.devpilot.sandbox.audit.AuditLogger;
import com.devpilot.sandbox.config.PolicyConfig;
import com.devpilot.sandbox.guard.CommandGuard;
import com.devpilot.sandbox.guard.PathGuard;
import com.devpilot.sandbox.model.GuardResult;
import com.devpilot.sandbox.model.RolePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/guard")
public class GuardController {

    private static final Logger log = LoggerFactory.getLogger(GuardController.class);

    @Autowired
    private PolicyConfig policyConfig;

    @Autowired
    private AuditLogger auditLogger;

    private final PathGuard pathGuard = new PathGuard();
    private final CommandGuard commandGuard = new CommandGuard();

    // Concurrency control: semaphore per role
    private final Map<String, Semaphore> concurrencyLocks = new ConcurrentHashMap<>();
    // Stats per role
    private final Map<String, Long> blockedCommandsStats = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedFilesStats = new ConcurrentHashMap<>();
    private long totalRequests = 0;
    private long totalBlocked = 0;

    private Semaphore getSemaphore(String roleId) {
        return concurrencyLocks.computeIfAbsent(roleId, k -> {
            RolePolicy policy = policyConfig.getRole(roleId);
            int permits = (policy != null) ? policy.getMaxConcurrency() : 1;
            if (permits < 1) permits = 1;
            return new Semaphore(permits);
        });
    }

    private boolean tryAcquire(String roleId, long timeoutMs) {
        try {
            Semaphore sem = getSemaphore(roleId);
            boolean acquired = sem.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("Concurrency limit reached for role: {} (timeout: {}ms)", roleId, timeoutMs);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void release(String roleId) {
        Semaphore sem = concurrencyLocks.get(roleId);
        if (sem != null) {
            sem.release();
        }
    }

    private synchronized void recordStats(boolean allowed, boolean isCommand, String roleId) {
        totalRequests++;
        if (!allowed) {
            totalBlocked++;
            if (isCommand) {
                blockedCommandsStats.merge(roleId, 1L, Long::sum);
            } else {
                blockedFilesStats.merge(roleId, 1L, Long::sum);
            }
        }
    }

    private RolePolicy resolveRole(String roleId) {
        if (roleId == null || roleId.isEmpty()) {
            if ("single".equals(policyConfig.getMode())) {
                java.util.Set<String> roles = policyConfig.getAvailableRoles();
                if (!roles.isEmpty()) {
                    return policyConfig.getRole(roles.iterator().next());
                }
            }
            return null;
        }
        return policyConfig.getRole(roleId);
    }

    @PostMapping("/file-read")
    public ResponseEntity<Map<String, Object>> checkFileRead(@RequestBody Map<String, Object> request) {
        return handleFileCheck(request, "read");
    }

    @PostMapping("/file-write")
    public ResponseEntity<Map<String, Object>> checkFileWrite(@RequestBody Map<String, Object> request) {
        return handleFileCheck(request, "write");
    }

    @PostMapping("/command-run")
    public ResponseEntity<Map<String, Object>> checkCommandRun(@RequestBody Map<String, Object> request) {
        String roleId = (String) request.get("roleId");
        String command = (String) request.get("command");

        if (command == null) {
            return badRequest("Missing command");
        }
        if (roleId == null && !"single".equals(policyConfig.getMode())) {
            return badRequest("Missing roleId");
        }

        RolePolicy policy = resolveRole(roleId);
        if (policy == null) {
            return roleNotFound(roleId);
        }

        String effectiveRoleId = roleId != null ? roleId : policy.getId();
        boolean acquired = tryAcquire(effectiveRoleId, 5000);
        if (!acquired) {
            Map<String, Object> body = new HashMap<>();
            body.put("allowed", false);
            body.put("reason", "Concurrency limit exceeded (max " + policy.getMaxConcurrency() + ")");
            body.put("maxConcurrency", policy.getMaxConcurrency());
            return ResponseEntity.status(429).body(body);
        }

        try {
            GuardResult result = commandGuard.checkCommand(policy, command);

            Map<String, Object> details = new HashMap<>();
            details.put("command", command);
            auditLogger.log("command-run", effectiveRoleId, result.isAllowed(), result.getReason(), details);

            recordStats(result.isAllowed(), true, effectiveRoleId);

            return response(result);
        } finally {
            release(effectiveRoleId);
        }
    }

    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> getAuditLog(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        List<String> entries = auditLogger.query(role, operation, limit, offset);
        Map<String, Object> result = new HashMap<>();
        result.put("logFile", auditLogger.getLogFilePath());
        result.put("totalReturned", entries.size());
        result.put("entries", entries);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "UP");
        body.put("version", "0.2.0");
        body.put("roles", policyConfig.getAvailableRoles());
        body.put("policySource", policyConfig.getLoadedFrom());
        body.put("lastLoaded", policyConfig.getLastLoaded());
        body.put("mode", policyConfig.getMode());
        body.put("totalRequests", totalRequests);
        body.put("totalBlocked", totalBlocked);
        body.put("blockedRate", totalRequests > 0 ? String.format("%.1f%%", (double) totalBlocked / totalRequests * 100) : "0%");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> body = new HashMap<>();
        body.put("totalRequests", totalRequests);
        body.put("totalBlocked", totalBlocked);
        body.put("blockedRate", totalRequests > 0 ? String.format("%.1f%%", (double) totalBlocked / totalRequests * 100) : "0%");
        body.put("blockedCommandsByRole", new HashMap<>(blockedCommandsStats));
        body.put("blockedFilesByRole", new HashMap<>(blockedFilesStats));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/dangerous-commands")
    public ResponseEntity<Map<String, Object>> getDangerousCommands() {
        Map<String, Object> body = new HashMap<>();
        body.put("dangerous", commandGuard.getDangerousCommandsList());
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> handleFileCheck(Map<String, Object> request, String accessType) {
        String roleId = (String) request.get("roleId");
        String path = (String) request.get("path");

        if (path == null) {
            return badRequest("Missing path");
        }
        if (roleId == null && !"single".equals(policyConfig.getMode())) {
            return badRequest("Missing roleId");
        }

        RolePolicy policy = resolveRole(roleId);
        if (policy == null) {
            return roleNotFound(roleId);
        }

        String effectiveRoleId = roleId != null ? roleId : policy.getId();
        boolean acquired = tryAcquire(effectiveRoleId, 5000);
        if (!acquired) {
            Map<String, Object> body = new HashMap<>();
            body.put("allowed", false);
            body.put("reason", "Concurrency limit exceeded (max " + policy.getMaxConcurrency() + ")");
            return ResponseEntity.status(429).body(body);
        }

        try {
            GuardResult result = "read".equals(accessType)
                    ? pathGuard.checkRead(policy, path)
                    : pathGuard.checkWrite(policy, path);

            Map<String, Object> details = new HashMap<>();
            details.put("path", path);
            details.put("accessType", accessType);
            auditLogger.log("file-" + accessType, effectiveRoleId, result.isAllowed(), result.getReason(), details);

            recordStats(result.isAllowed(), false, effectiveRoleId);

            return response(result);
        } finally {
            release(effectiveRoleId);
        }
    }

    private ResponseEntity<Map<String, Object>> response(GuardResult result) {
        Map<String, Object> body = new HashMap<>();
        body.put("allowed", result.isAllowed());
        body.put("resolvedPath", result.getResolvedPath());
        body.put("reason", result.getReason());
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("allowed", false);
        body.put("reason", message);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> roleNotFound(String roleId) {
        Map<String, Object> body = new HashMap<>();
        body.put("allowed", false);
        body.put("reason", roleId != null ? "Role '" + roleId + "' not found" : "No valid role configured");
        return ResponseEntity.status(404).body(body);
    }
}
