package com.devpilot.sandbox.model;

import java.util.List;

public class RolePolicy {
    private String id;
    private String workDir;
    private List<String> allowedPaths;
    private List<String> deniedPaths;
    private List<CommandRule> allowedCommands;
    private Integer maxFileSizeMb;
    private boolean readonly;
    private List<String> denyFiles;
    private List<String> globalDenyFlags;
    private int commandTimeoutSec;
    private int maxConcurrency = 1;

    public RolePolicy() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkDir() { return workDir; }
    public void setWorkDir(String workDir) { this.workDir = workDir; }
    public List<String> getAllowedPaths() { return allowedPaths; }
    public void setAllowedPaths(List<String> allowedPaths) { this.allowedPaths = allowedPaths; }
    public List<String> getDeniedPaths() { return deniedPaths; }
    public void setDeniedPaths(List<String> deniedPaths) { this.deniedPaths = deniedPaths; }
    public List<CommandRule> getAllowedCommands() { return allowedCommands; }
    public void setAllowedCommands(List<CommandRule> allowedCommands) { this.allowedCommands = allowedCommands; }
    public Integer getMaxFileSizeMb() { return maxFileSizeMb; }
    public void setMaxFileSizeMb(Integer maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }
    public boolean isReadonly() { return readonly; }
    public void setReadonly(boolean readonly) { this.readonly = readonly; }
    public List<String> getDenyFiles() { return denyFiles; }
    public void setDenyFiles(List<String> denyFiles) { this.denyFiles = denyFiles; }
    public List<String> getGlobalDenyFlags() { return globalDenyFlags; }
    public void setGlobalDenyFlags(List<String> globalDenyFlags) { this.globalDenyFlags = globalDenyFlags; }
    public int getCommandTimeoutSec() { return commandTimeoutSec; }
    public void setCommandTimeoutSec(int commandTimeoutSec) { this.commandTimeoutSec = commandTimeoutSec; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }

    public static class CommandRule {
        private String command;
        private List<String> subcommands;
        private List<String> denyFlags;
        private List<String> requireEnv;

        public CommandRule() {}

        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public List<String> getSubcommands() { return subcommands; }
        public void setSubcommands(List<String> subcommands) { this.subcommands = subcommands; }
        public List<String> getDenyFlags() { return denyFlags; }
        public void setDenyFlags(List<String> denyFlags) { this.denyFlags = denyFlags; }
        public List<String> getRequireEnv() { return requireEnv; }
        public void setRequireEnv(List<String> requireEnv) { this.requireEnv = requireEnv; }
    }
}
