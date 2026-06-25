package com.devpilot.sandbox.guard;

import com.devpilot.sandbox.model.GuardResult;
import com.devpilot.sandbox.model.RolePolicy;
import com.devpilot.sandbox.model.RolePolicy.CommandRule;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class CommandGuard {

    private static final Map<String, String[]> DANGEROUS_COMMANDS = buildDangerousCommands();

    private static Map<String, String[]> buildDangerousCommands() {
        Map<String, String[]> map = new HashMap<>();

        map.put("format", new String[]{"any"});
        map.put("format.com", new String[]{"any"});
        map.put("diskpart", new String[]{"any"});
        map.put("shutdown", new String[]{"any"});
        map.put("shutdown.exe", new String[]{"any"});
        map.put("reg", new String[]{"delete", "add", "import"});
        map.put("reg.exe", new String[]{"delete", "add", "import"});
        map.put("regedit", new String[]{"any"});
        map.put("sc", new String[]{"stop", "delete", "config"});
        map.put("sc.exe", new String[]{"stop", "delete", "config"});
        map.put("bcdedit", new String[]{"any"});
        map.put("schtasks", new String[]{"create", "delete", "change", "end"});
        map.put("takeown", new String[]{"any"});
        map.put("icacls", new String[]{"grant", "deny", "remove", "reset"});
        map.put("cacls", new String[]{"grant", "deny", "remove"});
        map.put("subst", new String[]{"any"});
        map.put("net", new String[]{"user", "localgroup", "stop", "start", "share", "use"});
        map.put("net1", new String[]{"user", "localgroup", "stop", "start"});
        map.put("wmic", new String[]{"delete", "call"});
        map.put("cipher", new String[]{"/w", "/d"});
        map.put("fsutil", new String[]{"any"});
        map.put("powercfg", new String[]{"any"});
        map.put("del", new String[]{"/f", "/s", "/q"});
        map.put("erase", new String[]{"/f", "/s"});
        map.put("rmdir", new String[]{"/s", "/q"});
        map.put("rd", new String[]{"/s", "/q"});
        map.put("move", new String[]{"any"});
        map.put("rename", new String[]{"any"});
        map.put("ren", new String[]{"any"});
        map.put("replace", new String[]{"any"});
        map.put("msg", new String[]{"any"});
        map.put("wevtutil", new String[]{"cl", "clear-log"});

        map.put("rm", new String[]{"-rf", "-r", "-f", "--recursive", "--force"});
        map.put("chmod", new String[]{"777", "a+rwx", "-R", "--recursive"});
        map.put("chown", new String[]{"any"});
        map.put("dd", new String[]{"any"});
        map.put("mkfs", new String[]{"any"});
        map.put("fdisk", new String[]{"any"});
        map.put("sudo", new String[]{"any"});
        map.put("su", new String[]{"any"});
        map.put("passwd", new String[]{"any"});
        map.put("kill", new String[]{"-9", "-KILL"});
        map.put("killall", new String[]{"any"});
        map.put("pkill", new String[]{"any"});
        map.put("reboot", new String[]{"any"});
        map.put("halt", new String[]{"any"});
        map.put("poweroff", new String[]{"any"});
        map.put("init", new String[]{"0", "6", "1"});
        map.put("systemctl", new String[]{"stop", "disable", "mask", "poweroff", "reboot", "halt"});
        map.put("mount", new String[]{"any"});
        map.put("umount", new String[]{"any"});

        return map;
    }

    private static final String DANGEROUS_REASON = "Dangerous command blocked (built-in blacklist)";

    public GuardResult checkCommand(RolePolicy policy, String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return GuardResult.denied("Command cannot be empty");
        }

        String[] parts = parseCommandLine(commandLine);
        if (parts.length == 0) {
            return GuardResult.denied("Cannot parse command");
        }

        String baseCommand = parts[0];
        String subcommand = parts.length > 1 ? parts[1] : null;

        // Step 1: Built-in dangerous command detection
        String[] dangerousFlags = DANGEROUS_COMMANDS.get(baseCommand);
        if (dangerousFlags != null) {
            for (String flag : dangerousFlags) {
                if ("any".equals(flag)) {
                    return GuardResult.denied(DANGEROUS_REASON + ": " + baseCommand);
                }
                if (subcommand != null && flag.equals(subcommand)) {
                    return GuardResult.denied(DANGEROUS_REASON + ": " + baseCommand + " " + subcommand);
                }
                for (int i = 1; i < parts.length; i++) {
                    if (flag.equals(parts[i])) {
                        return GuardResult.denied(DANGEROUS_REASON + ": " + baseCommand + " flag " + flag);
                    }
                }
            }
        }

        List<CommandRule> allowedCommands = policy.getAllowedCommands();
        if (allowedCommands == null || allowedCommands.isEmpty()) {
            return GuardResult.denied("No allowed commands configured for this role");
        }

        CommandRule matchedRule = null;
        for (CommandRule rule : allowedCommands) {
            if (rule.getCommand().equals(baseCommand)) {
                matchedRule = rule;
                break;
            }
        }

        if (matchedRule == null) {
            return GuardResult.denied("Command '" + baseCommand + "' not in whitelist");
        }

        List<String> allowedSubcommands = matchedRule.getSubcommands();
        if (allowedSubcommands != null && !allowedSubcommands.isEmpty()) {
            if (subcommand == null) {
                return GuardResult.denied("Command '" + baseCommand + "' requires a subcommand");
            }
            boolean subcommandAllowed = false;
            for (String allowed : allowedSubcommands) {
                if (allowed.equals(subcommand)) {
                    subcommandAllowed = true;
                    break;
                }
            }
            if (!subcommandAllowed) {
                return GuardResult.denied("Subcommand '" + subcommand + "' not allowed");
            }
        }

        List<String> denyFlags = matchedRule.getDenyFlags();
        if (denyFlags != null && !denyFlags.isEmpty()) {
            for (String flag : denyFlags) {
                for (int i = 1; i < parts.length; i++) {
                    if (parts[i].equals(flag)) {
                        return GuardResult.denied("Flag denied: '" + flag + "'");
                    }
                }
            }
        }

        List<String> globalDenyFlags = policy.getGlobalDenyFlags();
        if (globalDenyFlags != null && !globalDenyFlags.isEmpty()) {
            for (int i = 1; i < parts.length; i++) {
                for (String globalFlag : globalDenyFlags) {
                    if (parts[i].equals(globalFlag)) {
                        return GuardResult.denied("Global flag denied: '" + globalFlag + "'");
                    }
                }
            }
        }

        List<String> requireEnv = matchedRule.getRequireEnv();
        if (requireEnv != null && !requireEnv.isEmpty()) {
            for (String envVar : requireEnv) {
                String value = System.getenv(envVar);
                if (value == null || value.trim().isEmpty()) {
                    return GuardResult.denied("Environment variable required: '" + envVar + "'");
                }
            }
        }

        return GuardResult.allowed(commandLine);
    }


    public java.util.List<String> getDangerousCommandsList() {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (Map.Entry<String, String[]> entry : DANGEROUS_COMMANDS.entrySet()) {
            String cmd = entry.getKey();
            String[] flags = entry.getValue();
            if (flags.length == 1 && "any".equals(flags[0])) {
                list.add(cmd);
            } else {
                list.add(cmd + " (" + String.join(", ", flags) + ")");
            }
        }
        java.util.Collections.sort(list);
        return list;
    }

    private String[] parseCommandLine(String cmd) {
        return CommandParser.parse(cmd);
    }
}
