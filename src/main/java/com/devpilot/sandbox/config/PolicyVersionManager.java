package com.devpilot.sandbox.config;

import com.devpilot.sandbox.model.RolePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class PolicyVersionManager {
    private static final Logger log = LoggerFactory.getLogger(PolicyVersionManager.class);
    private static final int MAX_VERSIONS = 50;
    private final Path versionsDir;
    private final ObjectMapper mapper;

    public PolicyVersionManager() {
        this.versionsDir = Paths.get("logs", "versions");
        this.mapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        try { Files.createDirectories(versionsDir); }
        catch (IOException e) { log.warn("Cannot create versions dir: {}", e.getMessage()); }
    }

    public String saveVersion(List<RolePolicy> roles, String desc) {
        try {
            String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            Map<String, Object> ver = new HashMap<>();
            ver.put("id", ts); ver.put("timestamp", System.currentTimeMillis());
            ver.put("description", desc); ver.put("roles", roles);
            mapper.writerWithDefaultPrettyPrinter().writeValue(versionsDir.resolve(ts + ".json").toFile(), ver);
            cleanup();
            return ts;
        } catch (IOException e) { log.error("Save version failed: {}", e.getMessage()); return null; }
    }

    public List<Map<String, Object>> listVersions() {
        List<Map<String, Object>> versions = new ArrayList<>();
        File[] files = versionsDir.toFile().listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return versions;
        for (File f : files) {
            try {
                Map<String, Object> data = mapper.readValue(f, Map.class);
                Map<String, Object> info = new HashMap<>();
                info.put("id", data.get("id")); info.put("timestamp", data.get("timestamp"));
                info.put("description", data.get("description"));
                versions.add(info);
            } catch (IOException e) { log.warn("Cannot read version: {}", f.getName()); }
        }
        versions.sort((a, b) -> ((String) b.get("id")).compareTo((String) a.get("id")));
        return versions;
    }

    @SuppressWarnings("unchecked")
    public List<RolePolicy> loadVersion(String versionId) {
        try {
            Path file = versionsDir.resolve(versionId + ".json");
            if (!Files.exists(file)) return null;
            Map<String, Object> data = mapper.readValue(file.toFile(), Map.class);
            List<Map<String, Object>> roleMaps = (List<Map<String, Object>>) data.get("roles");
            return roleMaps.stream().map(m -> mapper.convertValue(m, RolePolicy.class)).collect(Collectors.toList());
        } catch (IOException e) { log.error("Load version failed: {}", e.getMessage()); return null; }
    }

    private void cleanup() {
        File[] files = versionsDir.toFile().listFiles((d, n) -> n.endsWith(".json"));
        if (files != null && files.length > MAX_VERSIONS) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < files.length - MAX_VERSIONS; i++) files[i].delete();
        }
    }
}