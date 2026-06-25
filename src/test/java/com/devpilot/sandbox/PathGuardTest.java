package com.devpilot.sandbox;

import com.devpilot.sandbox.guard.PathGuard;
import com.devpilot.sandbox.model.GuardResult;
import com.devpilot.sandbox.model.RolePolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;

public class PathGuardTest {

    private final PathGuard guard = new PathGuard();

    private RolePolicy createPolicy(String workDir) {
        RolePolicy policy = new RolePolicy();
        policy.setId("TEST");
        policy.setWorkDir(workDir);
        policy.setDeniedPaths(Collections.singletonList(".git"));
        policy.setMaxFileSizeMb(10);
        return policy;
    }
    @Test
    public void testReadonlyWriteDenied() {
        RolePolicy policy = createPolicy("C:/test");
        policy.setReadonly(true);
        GuardResult result = guard.checkWrite(policy, "test.txt");
        assertFalse(result.isAllowed(), "只读角色写入应被拒绝");
        assertTrue(result.getReason().contains("read-only"));
    }
    @Test
    public void testDenyFilesBlocked() {
        RolePolicy policy = createPolicy("C:/test");
        policy.setDenyFiles(java.util.Arrays.asList(".env"));
        GuardResult result = guard.checkRead(policy, ".env");
        assertFalse(result.isAllowed(), ".env 文件应被 denyFiles 拦截");
    }
    @Test
    public void testNormalFileAllowed() {
        RolePolicy policy = createPolicy("C:/test");
        policy.setDenyFiles(java.util.Arrays.asList(".env"));
        GuardResult result = guard.checkRead(policy, "README.md");
        assertTrue(result.isAllowed(), "普通文件应被允许");
    }

    @Test
    public void testNormalPathAllowed() {
        RolePolicy policy = createPolicy("C:/Users/test/project");
        GuardResult result = guard.checkRead(policy, "src/main/java/App.java");
        assertTrue(result.isAllowed(), "正常路径应被允许");
        assertNotNull(result.getResolvedPath());
    }

    @Test
    public void testPathTraversalDenied() {
        RolePolicy policy = createPolicy("C:/Users/test/project");
        GuardResult result = guard.checkRead(policy, "../../etc/passwd");
        assertFalse(result.isAllowed(), "路径穿越应被拒绝");
        assertTrue(result.getReason().contains("outside"));
    }

    @Test
    public void testDotDotEscape() {
        RolePolicy policy = createPolicy("C:/Users/test/project");
        GuardResult result = guard.checkRead(policy, "src/../../../Windows/System32");
        assertFalse(result.isAllowed(), "多层穿越应被拒绝");
    }

    @Test
    public void testDeniedPathBlocked() {
        RolePolicy policy = createPolicy("C:/Users/test/project");
        GuardResult result = guard.checkRead(policy, ".git/config");
        assertFalse(result.isAllowed(), "拒绝列表中的路径应被拦截");
    }

    @Test
    public void testEmptyPath() {
        RolePolicy policy = createPolicy("C:/Users/test/project");
        GuardResult result = guard.checkRead(policy, "");
        assertFalse(result.isAllowed(), "空路径应被拒绝");
    }

    @Test
    public void testWorkDirItself() {
        RolePolicy policy = createPolicy("C:/Users/test/project");
        GuardResult result = guard.checkRead(policy, ".");
        assertTrue(result.isAllowed(), "工作目录自身应被允许");
    }
}
