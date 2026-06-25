package com.devpilot.sandbox.model;

import java.time.Instant;
import java.util.Map;

public class AuditLog {
    private Instant timestamp;
    private String operation;
    private String roleId;
    private boolean allowed;
    private String reason;
    private Map<String, Object> details;

    public AuditLog() {}

    public AuditLog(String operation, String roleId, boolean allowed, String reason, Map<String, Object> details) {
        this.timestamp = Instant.now();
        this.operation = operation;
        this.roleId = roleId;
        this.allowed = allowed;
        this.reason = reason;
        this.details = details;
    }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }
    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}
