package com.devpilot.sandbox.controller;

import com.devpilot.sandbox.audit.AuditLogger;
import com.devpilot.sandbox.config.PolicyConfig;
import com.devpilot.sandbox.config.PolicyVersionManager;
import com.devpilot.sandbox.model.RolePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/policy")
public class PolicyAdminController {

    @Autowired
    private PolicyConfig policyConfig;

    @Autowired
    private AuditLogger auditLogger;

    @Autowired
    private PolicyVersionManager versionManager;

    @GetMapping("/roles")
    public ResponseEntity<List<RolePolicy>> listRoles() {
        List<RolePolicy> roles = new ArrayList<>();
        for (String id : policyConfig.getAvailableRoles()) {
            roles.add(policyConfig.getRole(id));
        }
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/roles/{id}")
    public ResponseEntity<RolePolicy> getRole(@PathVariable String id) {
        RolePolicy role = policyConfig.getRole(id);
        if (role == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(role);
    }

    @PutMapping("/roles")
    public ResponseEntity<Map<String, Object>> putRole(@RequestBody RolePolicy role) {
        Map<String, Object> result = new HashMap<>();
        if (role.getId() == null || role.getId().trim().isEmpty()) {
            result.put("error", "roleId is required");
            return ResponseEntity.badRequest().body(result);
        }
        policyConfig.addRole(role);        versionManager.saveVersion(getCurrentRoles(), "Update role: " + role.getId());
        auditLogger.log("policy-update", role.getId(), true, "Role updated via API");
        result.put("status", "ok");
        result.put("roleId", role.getId());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Map<String, Object>> deleteRole(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        policyConfig.removeRole(id);        versionManager.saveVersion(getCurrentRoles(), "Delete role: " + id);
        auditLogger.log("policy-delete", id, true, "Role deleted via API");
        result.put("status", "ok");
        result.put("roleId", id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadPolicy() {
        policyConfig.forceReload();
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("roles", policyConfig.getAvailableRoles().size());
        return ResponseEntity.ok(result);
    }
    private List<RolePolicy> getCurrentRoles() {
        List<RolePolicy> roles = new ArrayList<>();
        for (String id : policyConfig.getAvailableRoles()) {
            roles.add(policyConfig.getRole(id));
        }
        return roles;
    }

    @GetMapping("/versions")
    public ResponseEntity<List<Map<String, Object>>> listVersions() {
        return ResponseEntity.ok(versionManager.listVersions());
    }


    @GetMapping("/yaml")
    public ResponseEntity<String> getPolicyYaml() {
        try {
            java.util.Map<String, Object> root = new java.util.HashMap<>();
            root.put("mode", policyConfig.getMode());
            java.util.List<RolePolicy> roles = new java.util.ArrayList<>();
            for (String roleId : policyConfig.getAvailableRoles()) {
                RolePolicy role = policyConfig.getRole(roleId);
                if (role != null) roles.add(role);
            }
            root.put("roles", roles);
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            String output = yaml.dump(root);
            return ResponseEntity.ok()
                .header("Content-Type", "text/yaml;charset=UTF-8")
                .body(output);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    @PostMapping("/rollback/{id}")
    public ResponseEntity<Map<String, Object>> rollback(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        List<RolePolicy> roles = versionManager.loadVersion(id);
        if (roles == null) {
            result.put("error", "Version not found: " + id);
            return ResponseEntity.notFound().build();
        }
        policyConfig.getAvailableRoles().clear();
        for (RolePolicy r : roles) {
            policyConfig.addRole(r);
        }
        auditLogger.log("policy-rollback", id, true, "Rolled back to version " + id);
        result.put("status", "ok");
        result.put("versionId", id);
        result.put("roles", roles.size());
        return ResponseEntity.ok(result);
    }
}
