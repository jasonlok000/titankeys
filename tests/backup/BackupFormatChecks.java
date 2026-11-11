package com.atlas.engine;

import java.nio.charset.StandardCharsets;

public final class BackupFormatChecks {
    public static void main(String[] args) {
        String backup = "{\n  \"format\": \"physical-swipe-habits\",\n  \"version\": 2,\n  \"words\": {\"words\": []}\n}\n";

        // A backup, even one that is damaged, says what it is, so its failure is "can't read that file".
        same(claims(backup), true, "A whole backup names its format");
        same(claims(backup.substring(0, 40)), true, "A backup cut short still names its format");
        same(claims(backup.replace("\"version\": 2", "\"version\": 9")), true, "A backup from a newer version is a backup");
        same(claims(backup.replace("{\"words\": []}", "[1, 2")), true, "A backup with broken words is a backup");

        // Anything else is not a backup, whatever is wrong with it.
        same(claims(""), false, "An empty file is not a backup");
        same(claims("{\"snippets\": [{\"name\": \"addr\", \"text\": \"1 Main St\"}]}"), false, "A snippets file is not a word backup");
        same(claims("{\"format\": \"something-else\", \"version\": 2}"), false, "Another app's JSON is not a backup");
        same(BackupFormat.claimsToBe(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}), false, "A picture is not a backup");
        same(claims("physical-swipe-habits"), false, "The name alone, without quotes, is not the format field of a backup");

        // The two messages are the owner's words, and they differ.
        same(BackupFormat.NOT_A_BACKUP, "Yeah, that's not a TitanKeys backup. Pick the right file.", "The message for a file that is no backup");
        same(BackupFormat.DAMAGED, "Can't read that file, duh. Is it a TitanKeys backup?", "The message for a damaged backup");
        same(new BackupFormat.NotABackup("x") instanceof java.io.IOException, true, "Not a backup is still a failure to read");
        System.out.println("Backup format checks passed");
    }

    private static boolean claims(String text) {
        return BackupFormat.claimsToBe(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void same(Object actual, Object expected, String message) {
        if (!actual.equals(expected)) throw new AssertionError(message + "\n  expected: " + expected + "\n  actual:   " + actual);
    }
}
