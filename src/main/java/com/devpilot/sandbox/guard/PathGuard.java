package com.devpilot.sandbox.guard;

import com.devpilot.sandbox.model.GuardResult;
import com.devpilot.sandbox.model.RolePolicy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class PathGuard {

    private static final Logger log = LoggerFactory.getLogger(PathGuard.class);

    public GuardResult checkRead(RolePolicy policy, String inputPath) {
        return checkPath(policy, inputPath, "read");
    }

    public GuardResult checkWrite(RolePolicy policy, String inputPath) {
        return checkPath(policy, inputPath, "write");
    }

    private GuardResult checkPath(RolePolicy policy, String inputPath, String accessType) {
        if (inputPath == null || inputPath.trim().isEmpty()) {
            return GuardResult.denied("Path cannot be empty");
        }

        String workDir = policy.getWorkDir();
        if (workDir == null || workDir.trim().isEmpty()) {
            return GuardResult.denied("Work directory not configured");
        }

        try {
            // Resolve input path relative to workDir
            Path workDirPath = Paths.get(workDir).normalize().toAbsolutePath();
            if (!Files.exists(workDirPath)) {
                log.warn("Work directory does not exist: {}", workDirPath);
            }
            Path resolvedPath = workDirPath.resolve(inputPath).normalize();
            File resolvedFile = resolvedPath.toFile();

            // Check for canonical path (resolves .. and symlinks on some platforms)
            String canonicalPath = resolvedFile.getCanonicalPath();
            String workDirCanonical = workDirPath.toFile().getCanonicalPath();

            // Check symlink warning (informational, not a denial)
            boolean isSymLink = false;
            try {
                isSymLink = Files.isSymbolicLink(resolvedPath);
            } catch (Exception ignored) {}

            // Path traversal check: resolved path must be within workDir
            if (!canonicalPath.startsWith(workDirCanonical + File.separator)
                    && !canonicalPath.equals(workDirCanonical)) {
                return GuardResult.denied("Access denied：路径 '" + inputPath + "' is outside work directory");
            }

            // Check allowed paths whitelist
            List<String> allowedPaths = policy.getAllowedPaths();
            if (allowedPaths != null && !allowedPaths.isEmpty()) {
                boolean allowed = false;
                for (String allowedStr : allowedPaths) {
                    Path allowedResolved = workDirPath.resolve(allowedStr).normalize();
                    String allowedCanonical = allowedResolved.toFile().getCanonicalPath();
                    if (canonicalPath.startsWith(allowedCanonical + File.separator)
                            || canonicalPath.equals(allowedCanonical)) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    return GuardResult.denied("路径 '" + inputPath + "' is not in the allowed whitelist");
                }
            }

            // Check denied paths
            List<String> deniedPaths = policy.getDeniedPaths();
            if (deniedPaths != null) {
                for (String denied : deniedPaths) {
                    Path deniedResolved = workDirPath.resolve(denied).normalize();
                    String deniedCanonical = deniedResolved.toFile().getCanonicalPath();
                    if (canonicalPath.startsWith(deniedCanonical + File.separator)
                            || canonicalPath.equals(deniedCanonical)) {
                        return GuardResult.denied("路径 '" + inputPath + "' is denied by policy");
                    }
                }
            }
            // Check deny files blacklist
            List<String> denyFiles = policy.getDenyFiles();
            if (denyFiles != null && !denyFiles.isEmpty()) {
                String fileName = resolvedFile.getName();
                String normalizedPath = inputPath.replace("\\", "/");
                for (String denyFile : denyFiles) {
                    if (fileName.equals(denyFile) || normalizedPath.contains("/" + denyFile)) {
                        return GuardResult.denied("Access denied: '" + denyFile + "' is in the deny list");
                    }
                }
            }
            // Check readonly mode
            if ("write".equals(accessType) && policy.isReadonly()) {
                return GuardResult.denied("Write access denied: policy is read-only");
            }

            // Check max file size for writes
            if ("write".equals(accessType) && policy.getMaxFileSizeMb() != null && resolvedFile.exists()) {
                long maxBytes = policy.getMaxFileSizeMb().longValue() * 1024 * 1024;
                if (resolvedFile.length() > maxBytes) {
                    return GuardResult.denied("File size exceeds limit (" + policy.getMaxFileSizeMb() + " MB)");
                }
            }

            // Append symlink warning to resolved path if it's a link
            if (isSymLink) {
                try {
                    String realTarget = Files.readSymbolicLink(resolvedPath).toString();
                    canonicalPath = canonicalPath + " (symlink -> " + realTarget + ")";
                } catch (Exception ignored) {}
            }

            return GuardResult.allowed(canonicalPath);

        } catch (IOException e) {
            return GuardResult.denied("Path resolution error：" + e.getMessage());
        }
    }
}
