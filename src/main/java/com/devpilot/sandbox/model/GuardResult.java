package com.devpilot.sandbox.model;

public class GuardResult {
    private boolean allowed;
    private String resolvedPath;
    private String reason;

    public GuardResult() {}

    public GuardResult(boolean allowed, String resolvedPath, String reason) {
        this.allowed = allowed;
        this.resolvedPath = resolvedPath;
        this.reason = reason;
    }

    public static GuardResult allowed(String resolvedPath) {
        return new GuardResult(true, resolvedPath, null);
    }

    public static GuardResult denied(String reason) {
        return new GuardResult(false, null, reason);
    }

    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }
    public String getResolvedPath() { return resolvedPath; }
    public void setResolvedPath(String resolvedPath) { this.resolvedPath = resolvedPath; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
