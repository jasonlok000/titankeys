import com.titankeys.engine.Swipe;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Issue #83: runs the keyboard's own joining checks (Swipe.couldContinue and Swipe.sameStroke) on every pair that
 * pairs.py found. Reads pairs-points.txt on standard input and prints "pair couldContinue sameStroke" per line.
 * Lines carry the first swipe's end contact size (or NaN); the code from #83 on uses it. Pass "old" as the second
 * argument to run a Swipe.java from before #83, which has no size.
 * Compile it with android/kotlin/com/titankeys/engine/Swipe.java; it needs nothing else from the app.
 */
public final class JoinReplay {
    public static void main(String[] args) throws Exception {
        long flicker = args.length > 0 ? Long.parseLong(args[0]) : 90;
        boolean old = args.length > 1 && args[1].equals("old");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (String line; (line = in.readLine()) != null; ) {
            if (line.isBlank()) continue;
            String[] f = line.trim().split(" ");
            String pair = f[0];
            long gap = Long.parseLong(f[1]);
            float endContact = Float.parseFloat(f[2]);
            int na = Integer.parseInt(f[3]), nb = Integer.parseInt(f[4]);
            int at = 5;
            Swipe a = read(f, at, na);
            Swipe b = read(f, at + na * 3, nb);
            boolean could = a.couldContinue(b.x[0], b.y[0], gap, flicker);
            boolean same = old ? a.sameStroke(b, gap, flicker) : sameStroke(a, b, gap, flicker, endContact);
            out.append(pair).append(' ').append(could).append(' ').append(same).append('\n');
        }
        System.out.print(out);
    }

    /** Called by reflection so this file still compiles against a Swipe.java from before #83. */
    private static boolean sameStroke(Swipe a, Swipe b, long gap, long flicker, float endContact) throws Exception {
        return (Boolean) Swipe.class.getMethod("sameStroke", Swipe.class, long.class, long.class, float.class)
            .invoke(a, b, gap, flicker, endContact);
    }

    private static Swipe read(String[] f, int at, int n) {
        float[] x = new float[n], y = new float[n], t = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = Float.parseFloat(f[at + i * 3]);
            y[i] = Float.parseFloat(f[at + i * 3 + 1]);
            t[i] = Float.parseFloat(f[at + i * 3 + 2]);
        }
        return new Swipe(x, y, t);
    }
}
