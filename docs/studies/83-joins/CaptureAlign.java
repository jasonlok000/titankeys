package com.titankeys.engine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Issue #83: maps raw touchpad positions through the keyboard's built-in calibration, as Touchpad.samples does with
 * Calibration.selected(). Reads "padX padY" lines (0 to 1 each) on standard input and prints the aligned "x y".
 * Usage: java -cp classes com.titankeys.engine.CaptureAlign android/assets/calibration/titan-2-saved.profile
 * Compile it with android/kotlin/com/titankeys/engine/TouchAlignment.java; it needs nothing else from the app.
 */
public final class CaptureAlign {
    public static void main(String[] args) throws Exception {
        TouchAlignment alignment = TouchAlignment.decode(Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8).get(0));
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (String line; (line = in.readLine()) != null; ) {
            if (line.isBlank()) continue;
            String[] f = line.trim().split(" ");
            float x = Float.parseFloat(f[0]), y = Float.parseFloat(f[1]);
            out.append(alignment.x(x, y)).append(' ').append(alignment.y(y)).append('\n');
        }
        System.out.print(out);
    }
}
