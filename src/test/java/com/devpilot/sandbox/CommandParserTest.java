package com.devpilot.sandbox;

import com.devpilot.sandbox.guard.CommandParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CommandParserTest {

    @Test
    public void testSimpleCommand() {
        String[] parts = CommandParser.parse("git status");
        assertArrayEquals(new String[]{"git", "status"}, parts);
    }

    @Test
    public void testCommandWithDoubleQuotes() {
        String[] parts = CommandParser.parse("git commit -m \"fix bug\"");
        assertArrayEquals(new String[]{"git", "commit", "-m", "fix bug"}, parts);
    }

    @Test
    public void testCommandWithSingleQuotes() {
        String[] parts = CommandParser.parse("npm install 'express logger'");
        assertArrayEquals(new String[]{"npm", "install", "express logger"}, parts);
    }

    @Test
    public void testCommandWithMixedQuotes() {
        String[] parts = CommandParser.parse("echo \"hello 'world'\"");
        assertArrayEquals(new String[]{"echo", "hello 'world'"}, parts);
    }

    @Test
    public void testEscapeCharacter() {
        String[] parts = CommandParser.parse("echo hello\\ world");
        assertArrayEquals(new String[]{"echo", "hello world"}, parts);
    }

    @Test
    public void testEmptyString() {
        String[] parts = CommandParser.parse("");
        assertEquals(0, parts.length);
    }

    @Test
    public void testNullString() {
        String[] parts = CommandParser.parse(null);
        assertEquals(0, parts.length);
    }

    @Test
    public void testMultipleSpaces() {
        String[] parts = CommandParser.parse("git   status");
        assertArrayEquals(new String[]{"git", "status"}, parts);
    }

    @Test
    public void testExtractSubcommand() {
        String[] parts = CommandParser.parse("npm install express");
        assertEquals("install", CommandParser.extractSubcommand(parts));
    }

    @Test
    public void testHasFlag() {
        String[] parts = CommandParser.parse("npm install -g express");
        assertTrue(CommandParser.hasFlag(parts, "-g"));
        assertFalse(CommandParser.hasFlag(parts, "--save"));
    }

    @Test
    public void testNoSubcommand() {
        String[] parts = CommandParser.parse("git");
        assertNull(CommandParser.extractSubcommand(parts));
    }
}
