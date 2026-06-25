package com.devpilot.sandbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"sandbox.audit.console=false"})
public class SandboxIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1/guard" + path;
    }

    @Test
    public void testHealthEndpoint() {
        Map<String, Object> resp = rest.getForObject(url("/health"), Map.class);
        assertEquals("UP", resp.get("status"));
        assertNotNull(resp.get("roles"));
        assertTrue(((Collection) resp.get("roles")).size() >= 3);
    }

    @Test
    public void testFileReadAllowed() {
        Map<String, Object> body = mapOf("roleId", "CODE_ENGINEER", "path", "pom.xml");
        ResponseEntity<Map> resp = rest.postForEntity(url("/file-read"), body, Map.class);
        assertEquals(200, resp.getStatusCodeValue());
        assertTrue((Boolean) resp.getBody().get("allowed"));
    }

    @Test
    public void testFileReadPathTraversalDenied() {
        Map<String, Object> body = mapOf("roleId", "CODE_ENGINEER", "path", "../../etc/passwd");
        ResponseEntity<Map> resp = rest.postForEntity(url("/file-read"), body, Map.class);
        assertEquals(200, resp.getStatusCodeValue());
        assertFalse((Boolean) resp.getBody().get("allowed"));
        assertTrue(((String) resp.getBody().get("reason")).contains("outside"));
    }

    @Test
    public void testFileReadSensitivePathDenied() {
        Map<String, Object> body = mapOf("roleId", "CODE_ENGINEER", "path", ".git/config");
        ResponseEntity<Map> resp = rest.postForEntity(url("/file-read"), body, Map.class);
        assertEquals(200, resp.getStatusCodeValue());
        assertFalse((Boolean) resp.getBody().get("allowed"));
    }

    @Test
    public void testFileWriteAllowed() {
        Map<String, Object> body = mapOf("roleId", "CODE_ENGINEER", "path", "target/output.txt");
        ResponseEntity<Map> resp = rest.postForEntity(url("/file-write"), body, Map.class);
        assertEquals(200, resp.getStatusCodeValue());
        assertTrue((Boolean) resp.getBody().get("allowed"));
    }

    @Test
    public void testCommandAllowed() {
        Map<String, Object> body = mapOf("roleId", "CODE_ENGINEER", "command", "git status");
        ResponseEntity<Map> resp = rest.postForEntity(url("/command-run"), body, Map.class);
        assertEquals(200, resp.getStatusCodeValue());
        assertTrue((Boolean) resp.getBody().get("allowed"));
    }

    @Test
    public void testCommandDenied() {
        Map<String, Object> body = mapOf("roleId", "CODE_ENGINEER", "command", "rm -rf /");
        ResponseEntity<Map> resp = rest.postForEntity(url("/command-run"), body, Map.class);
        assertEquals(200, resp.getStatusCodeValue());
        assertFalse((Boolean) resp.getBody().get("allowed"));
    }

    @Test
    public void testCommandDeniedFlag() {
        Map<String, Object> body = mapOf("roleId", "CODE_ENGINEER", "command", "npm install -g express");
        ResponseEntity<Map> resp = rest.postForEntity(url("/command-run"), body, Map.class);
        assertEquals(200, resp.getStatusCodeValue());
        assertFalse((Boolean) resp.getBody().get("allowed"));
    }

    @Test
    public void testCommandWithQuotedParam() {
        Map<String, Object> body = mapOf("roleId", "CODE_ENGINEER", "command", "git commit -m \"fix bug\"");
        ResponseEntity<Map> resp = rest.postForEntity(url("/command-run"), body, Map.class);
        assertEquals(200, resp.getStatusCodeValue());
        assertTrue((Boolean) resp.getBody().get("allowed"));
    }

    @Test
    public void testRoleNotFound() {
        Map<String, Object> body = mapOf("roleId", "NONEXISTENT", "path", "test.txt");
        ResponseEntity<Map> resp = rest.postForEntity(url("/file-read"), body, Map.class);
        assertEquals(404, resp.getStatusCodeValue());
    }

    @Test
    public void testBadRequestMissingParams() {
        Map<String, Object> body = new HashMap<>();
        ResponseEntity<Map> resp = rest.postForEntity(url("/command-run"), body, Map.class);
        assertEquals(400, resp.getStatusCodeValue());
    }

    @Test
    public void testAuditEndpoint() {
        Map<String, Object> resp = rest.getForObject(url("/audit?role=CODE_ENGINEER"), Map.class);
        assertNotNull(resp.get("logFile"));
    }

    // Java 8 compatible Map.of() replacement
    private static Map<String, Object> mapOf(String k1, String v1, String k2, String v2) {
        Map<String, Object> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static Map<String, Object> mapOf(String k1, String v1, String k2, String v2,
                                              String k3, String v3) {
        Map<String, Object> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }
}
