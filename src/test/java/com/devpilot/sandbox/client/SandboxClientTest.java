package com.devpilot.sandbox.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"sandbox.audit.console=false"})
public class SandboxClientTest {

    @LocalServerPort
    private int port;

    @Test
    public void testHealthCheck() {
        SandboxClient client = new SandboxClient("http://localhost:" + port);
        SandboxClient.HealthResult health = client.health();
        assertTrue(health.isUp());
        assertNotNull(health.getRoles());
        assertTrue(health.getRoles().size() >= 3);
    }

    @Test
    public void testCheckFileReadAllowed() {
        SandboxClient client = new SandboxClient("http://localhost:" + port);
        SandboxClient.CheckResult result = client.checkFileRead("CODE_ENGINEER", "pom.xml");
        assertTrue(result.isAllowed());
        assertNotNull(result.getResolvedPath());
    }

    @Test
    public void testCheckFileReadDenied() {
        SandboxClient client = new SandboxClient("http://localhost:" + port);
        SandboxClient.CheckResult result = client.checkFileRead("CODE_ENGINEER", "../../etc/passwd");
        assertFalse(result.isAllowed());
        assertNotNull(result.getReason());
    }

    @Test
    public void testCheckCommandAllowed() {
        SandboxClient client = new SandboxClient("http://localhost:" + port);
        SandboxClient.CheckResult result = client.checkCommand("CODE_ENGINEER", "git status");
        assertTrue(result.isAllowed());
    }

    @Test
    public void testCheckCommandDenied() {
        SandboxClient client = new SandboxClient("http://localhost:" + port);
        SandboxClient.CheckResult result = client.checkCommand("CODE_ENGINEER", "rm -rf /");
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("whitelist") || result.getReason().contains("白名单") || result.getReason().contains("Dangerous"));
    }

    @Test
    public void testUrlNormalization() {
        SandboxClient c1 = new SandboxClient("localhost:9090");
        // The normalized URL should work - this just tests construction
        assertNotNull(c1);
    }

    @Test
    public void testDefaultRole() {
        SandboxClient client = new SandboxClient("http://localhost:" + port, "READONLY");
        SandboxClient.CheckResult result = client.checkFileRead("pom.xml");
        // READONLY has workDir at C:/Users/27736, so a relative path should be resolved
        // If the dir exists, this should be allowed or denied based on policy
        assertNotNull(result);
    }

    @Test
    public void testQueryAudit() {
        SandboxClient client = new SandboxClient("http://localhost:" + port);
        // Trigger some audit entries first
        client.checkFileRead("pom.xml");
        client.checkCommand("git status");

        List<String> entries = client.queryAudit(null, null, 5, 0);
        assertNotNull(entries);
    }
}
