package com.titankeys.engine;

import java.nio.file.Files;
import java.nio.file.Path;

/** Prints the app's own calibration on a grid, for test_touch.py to hold gestures.py's copy against. */
public final class AlignmentDump {
    public static void main(String[] args) throws Exception {
        TouchAlignment alignment = TouchAlignment.decode(Files.readAllLines(Path.of(args[0])).get(0));
        for (int i = 0; i <= 40; i++) for (int j = 0; j <= 40; j++) {
            float x = i / 40f, y = j / 40f;
            System.out.println(x + " " + y + " " + alignment.x(x, y) + " " + alignment.y(y));
        }
    }
}
