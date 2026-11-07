package com.atlas.engine;

import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** The built-in Titan 2 calibration, which maps where a key reads on the touchpad to where it is printed. */
public final class Calibration {
    private static TouchAlignment builtIn;

    public static void init(Context context) {
        if (builtIn != null) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("calibration/titan-2-saved.profile"), StandardCharsets.UTF_8))) {
            builtIn = TouchAlignment.decode(reader.readLine());
        } catch (IOException error) {
            throw new IllegalStateException("Could not load saved Titan 2 calibration", error);
        }
    }

    public static TouchAlignment selected() { return builtIn; }

    private Calibration() {}
}
