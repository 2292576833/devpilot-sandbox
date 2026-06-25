package com.devpilot.sandbox.config;

import com.devpilot.sandbox.model.RolePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import org.yaml.snakeyaml.constructor.Constructor;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

@Component
public class PolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(PolicyConfig.class);

    @Value("${sandbox.policy.path:classpath:policy.yaml}")
    private String policyPath;

    @Value("${sandbox.policy.reload-seconds:0}")
    private int reloadSeconds;

    private final Map<String, RolePolicy> roleCache = new ConcurrentHashMap<>();
    private String loadedFrom;
    private long lastLoaded = 0;
    private String currentMode = "multi";
    private static final String STATE_FILE = "data/roles-state.json";
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private Timer reloadTimer;
    private Thread watchThread;
    private volatile boolean running = true;

    @PostConstruct
    public void init() {
        loadPolicy();
        loadState();
        startReloadMechanism();
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (reloadTimer != null) {
            reloadTimer.cancel();
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }

    private void startReloadMechanism() {
        // For filesystem paths: use WatchService for immediate reload
        if (loadedFrom != null && !loadedFrom.startsWith("classpath:")) {
            Path policyFile = new java.io.File(loadedFrom).toPath().toAbsolutePath();
            Path dir = policyFile.getParent();
            String fileName = policyFile.getFileName().toString();
            if (dir != null && Files.isDirectory(dir)) {
                startWatchService(dir, fileName);
                return;
            }
        }
        // Fallback: timer-based reload for classpath or inaccessible paths
        if (reloadSeconds > 0) {
            startTimerReload();
        }
    }

    private void startWatchService(Path dir, String fileName) {
        watchThread = new Thread(() -> {
            log.info("Policy file watcher started for: {}/{}", dir, fileName);
            try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                dir.register(watcher, ENTRY_MODIFY, ENTRY_CREATE);
                while (running && !Thread.currentThread().isInterrupted()) {
                    WatchKey key;
                    try {
                        key = watcher.poll(3, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    if (key == null) continue;

                    boolean changed = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changedFile = (Path) event.context();
                        if (changedFile.toString().equals(fileName)) {
                            changed = true;
                        }
                    }
                    if (changed) {
                        // Debounce: wait for additional changes
                        try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                        log.info("Policy file change detected, reloading...");
                        loadPolicy();
                    }
                    if (!key.reset()) break;
                }
            } catch (Exception e) {
                log.warn("WatchService stopped ({}), fallback to timer", e.getMessage());
            }
            if (reloadSeconds > 0) {
                startTimerReload();
            }
        }, "policy-watch");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    private synchronized void startTimerReload() {
        if (reloadTimer != null) return;
        reloadTimer = new Timer("policy-reload", true);
        reloadTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    loadPolicy();
                } catch (Exception e) {
                    log.warn("Timer reload failed: {}", e.getMessage());
                }
            }
        }, reloadSeconds * 1000L, reloadSeconds * 1000L);
        log.info("Timer-based policy reload started (interval={}s)", reloadSeconds);
    }

    
    /**
     * Pre-process YAML to support snake_case keys (e.g. work_dir -> workDir)
     */
    private String normalizeKeys(String yaml) {
        StringBuilder sb = new StringBuilder();
        String[] lines = yaml.split("\n", -1);
        for (String line : lines) {
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0 && !line.trim().startsWith("#")) {
                String beforeColon = line.substring(0, colonIdx);
                String afterColon = line.substring(colonIdx);
                String trimmed = beforeColon.trim();
                if (trimmed.contains("_")) {
                    String camel = snakeToCamel(trimmed);
                    int idx = line.indexOf(trimmed);
                    sb.append(line, 0, idx).append(camel).append(afterColon).append("\n");
                } else {
                    sb.append(line).append("\n");
                }
            } else {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private static String snakeToCamel(String s) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : s.toCharArray()) {
            if (c == '_') { nextUpper = true; }
            else if (nextUpper) { sb.append(Character.toUpperCase(c)); nextUpper = false; }
            else { sb.append(c); }
        }
        return sb.toString();
    }


    private void saveState() {
        try {
            File f = new File(STATE_FILE);
            f.getParentFile().mkdirs();
            jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
            jsonMapper.writeValue(f, roleCache.values());
            log.debug("Saved {} roles to {}", roleCache.size(), STATE_FILE);
        } catch (Exception e) {
            log.error("Failed to save roles state: {}", e.getMessage());
        }
    }

    private void loadState() {
        File f = new File(STATE_FILE);
        if (f.exists()) {
            try {
                RolePolicy[] roles = jsonMapper.readValue(f, RolePolicy[].class);
                for (RolePolicy r : roles) {
                    if (r.getId() != null) roleCache.put(r.getId(), r);
                }
                log.info("Loaded {} roles from {}", roles.length, STATE_FILE);
            } catch (Exception e) {
                log.error("Failed to load roles state: {}", e.getMessage());
            }
        }
    }

public synchronized void loadPolicy() {
        try {
            Yaml yaml = new Yaml(new Constructor(PolicyRoot.class));
            PolicyRoot root;

            if (policyPath.startsWith("classpath:")) {
                String cp = policyPath.substring("classpath:".length());
                InputStream is = getClass().getClassLoader().getResourceAsStream(cp);
                if (is == null) {
                    log.warn("Policy file not found on classpath: {}", cp);
                    return;
                }
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                            byte[] buf = new byte[4096]; int len;
                            while ((len = is.read(buf)) > 0) bos.write(buf, 0, len);
                            String yamlText = normalizeKeys(bos.toString("UTF-8"));
                            root = yaml.load(yamlText);
                is.close();
                loadedFrom = "classpath:" + cp;
            } else {
                try (InputStream is = new FileInputStream(policyPath)) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                            byte[] buf = new byte[4096]; int len;
                            while ((len = is.read(buf)) > 0) bos.write(buf, 0, len);
                            String yamlText = normalizeKeys(bos.toString("UTF-8"));
                            root = yaml.load(yamlText);
                }
                loadedFrom = new java.io.File(policyPath).getAbsolutePath();
            }

            if (root != null) {
                roleCache.clear();
                if ("single".equalsIgnoreCase(root.mode) && root.agent != null) {
                    String agentId = root.agent.getId() != null ? root.agent.getId() : "_agent_";
                    root.agent.setId(agentId);
                    roleCache.put(agentId, root.agent);
                    log.info("Single-agent mode: loaded agent '{}'", agentId);
                } else if (root.roles != null) {
                    for (RolePolicy role : root.roles) {
                        if (role.getId() != null) {
                            roleCache.put(role.getId(), role);
                            log.info("Loaded role: {}", role.getId());
                        }
                    }
                }
            }
            lastLoaded = System.currentTimeMillis();
            log.info("Policy loaded ({} roles) from {}", roleCache.size(), loadedFrom);

        } catch (Exception e) {
            log.error("Failed to load policy: {}", e.getMessage(), e);
        }
    }

    public RolePolicy getRole(String roleId) {
        return roleCache.get(roleId);
    }

    public Set<String> getAvailableRoles() {
        return roleCache.keySet();
    }

    public String getLoadedFrom() {
        return loadedFrom;
    }

    public long getLastLoaded() {
        return lastLoaded;
    }
    public String getMode() {
        return currentMode;
    }


    /**
     * 运行时添加或更新角色
     */
    public void addRole(RolePolicy role) {
        if (role != null && role.getId() != null) {
            roleCache.put(role.getId(), role);
            saveState();
            log.info("Role updated at runtime: {}", role.getId());
        }
    }

    /**
     * 运行时删除角色
     */
    public void removeRole(String roleId) {
        if (roleId != null) {
            RolePolicy removed = roleCache.remove(roleId);
            if (removed != null) {
                saveState();
                log.info("Role removed at runtime: {}", roleId);
            }
        }
    }

    /**
     * 从 YAML 文件强制重载策略
     */
    public void forceReload() {
        loadPolicy();
        saveState();
    }

    
    /**
     * Custom SnakeYAML Constructor that supports both snake_case and camelCase property names.
     * This allows policy.yaml to use either "work_dir" or "workDir" - both work.
     */
    public static class PolicyRoot {
        public String mode; // "single" or "multi"
        public RolePolicy agent; // single-agent config, null in multi mode
        public List<RolePolicy> roles;
    }
}
