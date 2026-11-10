package com.titankeys.reference;

import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

/** One JSON line for each mark, on the same clock as the raw input log. It never holds the text that was typed. */
final class Timeline {
    private final File file;
    private boolean failed;

    /** The directory is null when shared storage isn't available. */
    Timeline(File directory, String name) {
        file = directory == null ? null : new File(directory, name);
        failed = file == null;
    }

    static String newName() {
        return "timeline-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".jsonl";
    }

    String name() {
        return file == null ? newName() : file.getName();
    }

    boolean failed() {
        return failed;
    }

    /** A passage of 0 means that none was on screen, and sent is false if the keyboard could not get the mark. */
    void add(long time, String kind, String label, int step, int passage, boolean sent) {
        if (file == null) return;
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            JSONObject line = new JSONObject()
                .put("t", time)
                .put("wall", System.currentTimeMillis())
                .put("kind", kind)
                .put("label", label)
                .put("step", step)
                .put("sent", sent);
            if (passage > 0) line.put("passage", passage);
            writer.write(line.toString());
            writer.write('\n');
        } catch (IOException | JSONException error) {
            failed = true;
            Log.w("TitanKeysReference", "Could not write the timeline", error);
        }
    }
}
