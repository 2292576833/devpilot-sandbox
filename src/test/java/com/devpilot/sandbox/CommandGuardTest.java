package com.devpilot.sandbox;

import com.devpilot.sandbox.guard.CommandGuard;
import com.devpilot.sandbox.model.GuardResult;
import com.devpilot.sandbox.model.RolePolicy;
import com.devpilot.sandbox.model.RolePolicy.CommandRule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

public class CommandGuardTest {

    private final CommandGuard guard = new CommandGuard();

    private RolePolicy createGitPolicy() {
        RolePolicy policy = new RolePolicy();
        policy.setId("TEST");
        CommandRule gitRule = new CommandRule();
        gitRule.setCommand("git");
        gitRule.setSubcommands(Arrays.asList("status", "add", "commit", "pull", "push"));
        CommandRule npmRule = new CommandRule();
        npmRule.setCommand("npm");
        npmRule.setSubcommands(Arrays.asList("install", "run", "test"));
        npmRule.setDenyFlags(Arrays.asList("-g", "--global"));
        policy.setAllowedCommands(Arrays.asList(gitRule, npmRule));
        return policy;
    }

    @Test
    public void testAllowedGitCommand() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "git status");
        assertTrue(result.isAllowed(), "git status should be allowed");
    }

    @Test
    public void testGlobalDenyFlags() {
        RolePolicy policy = createGitPolicy();
        policy.setGlobalDenyFlags(Arrays.asList("-g", "--global"));
        GuardResult result = guard.checkCommand(policy, "git status -g");
        assertFalse(result.isAllowed(), "global deny flag -g should be blocked");
    }

    @Test
    public void testGlobalDenyFlagsAllowed() {
        RolePolicy policy = createGitPolicy();
        policy.setGlobalDenyFlags(Arrays.asList("-g", "--global"));
        GuardResult result = guard.checkCommand(policy, "git status");
        assertTrue(result.isAllowed(), "command without global flags should be allowed");
    }

    @Test
    public void testDisallowedSubcommand() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "git reset --hard");
        assertFalse(result.isAllowed(), "git reset should be denied");
    }

    @Test
    public void testDisallowedBaseCommand() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "rm -rf /");
        assertFalse(result.isAllowed(), "rm should be denied");
    }

    @Test
    public void testDeniedFlag() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "npm install -g express");
        assertFalse(result.isAllowed(), "denied flag -g should be blocked");
    }

    @Test
    public void testAllowedNpmCommand() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "npm run build");
        assertTrue(result.isAllowed(), "npm run build should be allowed");
    }

    @Test
    public void testQuotedCommandParsing() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "git commit -m \"initial commit\"");
        assertTrue(result.isAllowed(), "quoted args should be allowed");
    }

    @Test
    public void testEmptyCommand() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "");
        assertFalse(result.isAllowed(), "empty command should be denied");
    }

    @Test
    public void testNullCommand() {
        GuardResult result = guard.checkCommand(createGitPolicy(), null);
        assertFalse(result.isAllowed(), "null command should be denied");
    }

    @Test
    public void testDangerousFormatBlocked() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "format C: /q");
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("Dangerous"), "format should be blocked by dangerous commands list");
    }

    @Test
    public void testDangerousRmFlagBlocked() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "rm -rf /");
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("Dangerous"), "rm -rf should be blocked by dangerous commands list");
    }

    @Test
    public void testDangerousSudoBlocked() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "sudo apt-get install");
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("Dangerous"), "sudo should be blocked by dangerous commands list");
    }

    @Test
    public void testDangerousRegDeleteBlocked() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "reg delete HKLM /f");
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("Dangerous"), "reg delete should be blocked by dangerous commands list");
    }

    @Test
    public void testDangerousNetUserBlocked() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "net user admin pass /add");
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("Dangerous"), "net user should be blocked by dangerous commands list");
    }

    @Test
    public void testDangerousDelFBlocked() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "del /f /s /q C:\\*");
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("Dangerous"), "del /f should be blocked by dangerous commands list");
    }

    @Test
    public void testDangerousShutdownBlocked() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "shutdown /s /t 0");
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("Dangerous"), "shutdown should be blocked by dangerous commands list");
    }

    @Test
    public void testDangerousRebootBlocked() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "reboot");
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("Dangerous"), "reboot should be blocked by dangerous commands list");
    }

    @Test
    public void testDangerousDiskpartBlocked() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "diskpart");
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("Dangerous"), "diskpart should be blocked by dangerous commands list");
    }

    @Test
    public void testDangerousChmod777Blocked() {
        GuardResult result = guard.checkCommand(createGitPolicy(), "chmod 777 /etc/shadow");
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("Dangerous"), "chmod 777 should be blocked by dangerous commands list");
    }
}
