package com.atlas.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Tells a file that is no TitanKeys backup at all from a TitanKeys backup that is damaged, so that a failed import can say which.
 * Every backup names its format, so a file without that name is something else, whatever is wrong with it.
 * This needs nothing from Android, so tests/backup can check it.
 */
final class BackupFormat {
    static final String FORMAT = "physical-swipe-habits";

    /** For a file of the wrong type or format. */
    static final String NOT_A_BACKUP = "Yeah, that's not a TitanKeys backup. Pick the right file.";
    /** For a file that names the backup format but can't be read, or a file that can't be opened at all. */
    static final String DAMAGED = "Can't read that file, duh. Is it a TitanKeys backup?";

    private BackupFormat() {}

    /** True if [bytes] say they are a TitanKeys backup, whether or not the rest of the file can be read. */
    static boolean claimsToBe(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).contains("\"" + FORMAT + "\"");
    }

    /** The file is no TitanKeys backup at all, as opposed to a damaged one. */
    static final class NotABackup extends IOException {
        NotABackup(String message) {
            super(message);
        }
    }
}
