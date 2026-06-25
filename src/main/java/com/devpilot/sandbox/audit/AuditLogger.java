package com.devpilot.sandbox.audit;

import com.devpilot.sandbox.model.AuditLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private final File logFile;
    private final ObjectMapper mapper;
    private final boolean consoleOutput;
    private final long maxFileSizeBytes;
    private final int maxBackups;

    public AuditLogger(
            @Value("${sandbox.audit.path:logs/audit.jsonl}") String logFilePath,
            @Value("${sandbox.audit.console:true}") boolean consoleOutput,
            @Value("${sandbox.audit.max-file-size-mb:10}") int maxFileSizeMb,
            @Value("${sandbox.audit.max-backups:3}") int maxBackups) {
        this.logFile = new File(logFilePath);
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.consoleOutput = consoleOutput;
        this.maxFileSizeBytes = maxFileSizeMb * 1024L * 1024L;
        this.maxBackups = maxBackups;
    }

    @PostConstruct
    public void init() {
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        log.info("审计日志已初始化: {}", logFile.getAbsolutePath());
    }

    public synchronized void log(String operation, String roleId, boolean allowed,
                                  String reason, java.util.Map<String, Object> details) {
        rotateIfNeeded();
        AuditLog entry = new AuditLog(operation, roleId, allowed, reason, details);
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            String json = mapper.writeValueAsString(entry);
            writer.println(json);
            if (consoleOutput) {
                log.info("[AUDIT] {} | {} | {} | {}",
                        operation, roleId, allowed ? "ALLOWED" : "DENIED", reason);
            }
        } catch (IOException e) {
            log.error("审计日志写入失败: {}", e.getMessage(), e);
        }
    }

    public void log(String operation, String roleId, boolean allowed, String reason) {
        log(operation, roleId, allowed, reason, null);
    }

    /**
     * 查询审计日志，支持按角色、操作类型过滤，支持分页
     */
    public List<String> query(String role, String operation, Integer limit, Integer offset) {
        if (!logFile.exists()) {
            return Collections.emptyList();
        }

        int lim = (limit != null && limit > 0) ? limit : 50;
        int off = (offset != null && offset > 0) ? offset : 0;

        try {
            byte[] fileBytes = Files.readAllBytes(logFile.toPath());
            java.nio.charset.CharsetDecoder decoder = java.nio.charset.StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE);
            String content = decoder.decode(java.nio.ByteBuffer.wrap(fileBytes)).toString();
            String[] lines = content.split("\\r?\\n");
            java.util.List<String> result = new java.util.ArrayList<>();
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    if (role != null && !line.contains("\"roleId\":\"" + role + "\"")) continue;
                    if (operation != null && !line.contains("\"operation\":\"" + operation + "\"")) continue;
                    if (off > 0) { off--; continue; }
                    if (result.size() >= lim) break;
                    result.add(lines[i]);
                }
            }
            return result;
        } catch (IOException e) {
            log.error("读取审计日志失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void rotateIfNeeded() {
        if (maxFileSizeBytes <= 0 || !logFile.exists()) return;
        if (logFile.length() < maxFileSizeBytes) return;
        try {
            for (int i = maxBackups - 1; i >= 1; i--) {
                File b = new File(logFile.getAbsolutePath() + "." + i);
                File nb = new File(logFile.getAbsolutePath() + "." + (i + 1));
                if (b.exists()) b.renameTo(nb);
            }
            File b1 = new File(logFile.getAbsolutePath() + ".1");
            if (logFile.renameTo(b1)) {
                log.warn("Audit log rotated: {} -> {}.1", logFile.getName(), logFile.getName());
            }
        } catch (Exception e) {
            log.error("Audit log rotation failed: {}", e.getMessage());
        }
    }

    public String getLogFilePath() {
        return logFile.getAbsolutePath();
    }
}
