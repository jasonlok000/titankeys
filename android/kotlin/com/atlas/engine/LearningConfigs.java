package com.atlas.engine;

import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

/** Validated JSON storage, legacy migration, and a restart-safe import. */
final class LearningConfigs {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    final AtomicFile words;
    private final File legacyDirectory;
    private final AtomicFile pendingImport;
    private String importWarning = "";

    LearningConfigs(File directory, File legacyDirectory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create habits config directory");
        this.legacyDirectory = legacyDirectory;
        deleteGestureHabits(directory, legacyDirectory);
        words = new AtomicFile(new File(directory, "saved-words.json"));
        pendingImport = new AtomicFile(new File(directory, "pending-import.json"));
        if (hasPendingImport()) {
            LanguageHabits pending;
            try (InputStream input = pendingImport.openRead()) { pending = readExport(input); }
            catch (IOException invalid) {
                preserve(pendingImport);
                discardImport();
                importWarning = " · Damaged import file preserved";
                return;
            }
            completeImport(pending);
        }
    }

    LanguageHabits loadWords() throws IOException {
        ensureNoPendingImport();
        try { return readWords(words); }
        catch (FileNotFoundException absent) {
            if (previous(words).getBaseFile().exists()) {
                LanguageHabits recovered = readWords(previous(words));
                write(words, wordsJson(recovered));
                return recovered;
            }
            LanguageHabits loaded = new LanguageHabits();
            File legacy = new File(legacyDirectory, "personal-words-v1");
            if (legacy.exists() || new File(legacy + ".bak").exists())
                try (DataInputStream input = new DataInputStream(new AtomicFile(legacy).openRead())) { loaded.read(input); }
            saveWords(loaded);
            return loaded;
        } catch (IOException invalid) {
            LanguageHabits recovered = readWords(previous(words));
            write(words, wordsJson(recovered));
            return recovered;
        }
    }

    static LanguageHabits readWords(AtomicFile file) throws IOException {
        try (InputStream input = file.openRead()) { return decodeWords(readJson(input)); }
    }

    void saveWords(LanguageHabits value) throws IOException {
        ensureNoPendingImport(); save(words, wordsJson(value));
    }
    void replaceWords(LanguageHabits value) throws IOException {
        ensureNoPendingImport(); save(words, wordsJson(value), true);
    }

    boolean hasPendingImport() {
        return pendingImport.getBaseFile().exists() || new File(pendingImport.getBaseFile() + ".bak").exists();
    }

    String importWarning() { return importWarning; }

    private void ensureNoPendingImport() throws IOException {
        if (hasPendingImport()) throw new IOException("An earlier habits import has not finished");
    }

    private void discardImport() throws IOException {
        pendingImport.delete();
        if (hasPendingImport()) throw new IOException("Could not clear the habits import journal");
    }

    static JSONObject export(LanguageHabits words) throws IOException {
        try { return new JSONObject().put("format", BackupFormat.FORMAT).put("version", 2)
                .put("words", words.config()); }
        catch (JSONException error) { throw new IOException("Could not encode habits", error); }
    }

    /**
     * Version 1 backups also hold swipe habits, which are ignored. A file that does not name the backup format throws
     * [BackupFormat.NotABackup]; a backup that can't be read throws a plain IOException, so the import can say which.
     */
    static LanguageHabits readExport(InputStream input) throws IOException {
        byte[] bytes;
        try { bytes = readBytes(input); }
        // A backup is never this large, so this is some other file.
        catch (TooLarge error) { throw new BackupFormat.NotABackup("Too large for a habits config"); }
        if (!BackupFormat.claimsToBe(bytes)) throw new BackupFormat.NotABackup("Not a habits config");
        try {
            JSONObject root = parseJson(bytes);
            int version = root.getInt("version");
            if (!BackupFormat.FORMAT.equals(root.getString("format")) || version != 1 && version != 2)
                throw new IOException("Unsupported habits config");
            return decodeWords(root.getJSONObject("words"));
        } catch (JSONException error) { throw new IOException("Invalid habits config", error); }
    }

    void importWords(LanguageHabits imported) throws IOException {
        ensureNoPendingImport();
        write(pendingImport, export(imported));
        completeImport(imported);
        importWarning = "";
    }

    private void completeImport(LanguageHabits imported) throws IOException {
        save(words, wordsJson(imported), true);
        discardImport();
    }

    private static LanguageHabits decodeWords(JSONObject object) throws IOException {
        try { return LanguageHabits.fromConfig(object); }
        catch (JSONException | RuntimeException error) { throw new IOException("Invalid saved words config", error); }
    }
    private static JSONObject wordsJson(LanguageHabits value) throws IOException {
        try { return value.config(); } catch (JSONException error) { throw new IOException("Could not encode saved words", error); }
    }
    private static AtomicFile previous(AtomicFile file) { return new AtomicFile(new File(file.getBaseFile() + ".previous")); }

    private static void save(AtomicFile file, JSONObject value) throws IOException {
        save(file, value, false);
    }

    private static void save(AtomicFile file, JSONObject value, boolean replaceInvalid) throws IOException {
        JSONObject existing = null;
        try (InputStream input = file.openRead()) {
            existing = readJson(input);
            decodeWords(existing);
        } catch (FileNotFoundException absent) {
            if (replaceInvalid) preserve(previous(file));
        } catch (IOException invalid) {
            if (!replaceInvalid) throw invalid;
            preserve(file); preserve(previous(file));
            existing = null;
        }
        if (existing != null) write(previous(file), existing);
        write(file, value);
    }

    /** Swipe learning no longer exists, so remove what earlier versions saved. */
    private static void deleteGestureHabits(File directory, File legacyDirectory) {
        new AtomicFile(new File(directory, "gesture-habits.json")).delete();
        new AtomicFile(new File(directory, "gesture-habits.json.previous")).delete();
        new AtomicFile(new File(legacyDirectory, "gesture-habits-v1")).delete();
        File[] damaged = directory.listFiles((parent, name) -> name.startsWith("gesture-habits.json"));
        if (damaged != null) for (File file : damaged) file.delete();
    }

    private static void preserve(AtomicFile file) throws IOException {
        File source = file.getBaseFile();
        if (!source.exists() && !new File(source + ".bak").exists()) return;
        File copy = File.createTempFile(source.getName() + ".damaged-", ".json", source.getParentFile());
        try (InputStream input = file.openRead(); FileOutputStream output = new FileOutputStream(copy)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.getFD().sync();
        }
    }

    static JSONObject readJson(InputStream input) throws IOException { return parseJson(readBytes(input)); }

    private static final class TooLarge extends IOException {
        TooLarge() { super("Habits config is too large"); }
    }

    private static byte[] readBytes(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > MAX_BYTES) throw new TooLarge();
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }

    private static JSONObject parseJson(byte[] bytes) throws IOException {
        try { return new JSONObject(new String(bytes, StandardCharsets.UTF_8)); }
        catch (JSONException error) { throw new IOException("Invalid habits JSON", error); }
    }

    static void writeJson(OutputStream output, JSONObject value) throws IOException {
        try { output.write((value.toString(2) + "\n").getBytes(StandardCharsets.UTF_8)); }
        catch (JSONException error) { throw new IOException("Could not encode habits JSON", error); }
    }

    private static void write(AtomicFile file, JSONObject value) throws IOException {
        FileOutputStream stream = file.startWrite();
        try { writeJson(stream, value); file.finishWrite(stream); }
        catch (IOException | RuntimeException error) { file.failWrite(stream); throw error; }
    }
}
