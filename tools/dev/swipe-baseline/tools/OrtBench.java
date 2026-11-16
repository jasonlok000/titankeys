import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Times the new models on the phone with ONNX Runtime, CPU only. It adds no input and does not touch the keyboard.
 * Usage: OrtBench ck model.onnx | OrtBench lm model.onnx
 */
public class OrtBench {
    static long rssKb() throws Exception {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/status"))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.startsWith("VmRSS:")) return Long.parseLong(line.replaceAll("[^0-9]", ""));
            }
        }
        return -1;
    }

    interface Run { void run() throws Exception; }

    static String time(Run run, int warm, int runs) throws Exception {
        for (int i = 0; i < warm; i++) run.run();
        double[] ms = new double[runs];
        for (int i = 0; i < runs; i++) {
            long t = System.nanoTime();
            run.run();
            ms[i] = (System.nanoTime() - t) / 1e6;
        }
        Arrays.sort(ms);
        return String.format("median %.1f ms, slowest tenth from %.1f ms", ms[runs / 2], ms[(int) (runs * .9)]);
    }

    public static void main(String[] args) throws Exception {
        String kind = args[0], path = args[1];
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        System.out.println(new File(path).getName() + " " + new File(path).length() / 100000 / 10.0 + " MB on disk");
        // Memory-saving modes: "lean" turns off the memory arena, memory patterns and weight prepacking.
        String mode = args.length > 2 ? args[2] : "plain";
        for (int threads : mode.equals("plain") ? new int[]{1, 2, 4} : new int[]{4}) {
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(threads);
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            if (mode.contains("noarena")) { opts.setCPUArenaAllocator(false); opts.setMemoryPatternOptimization(false); }
            if (mode.contains("noprepack")) opts.addConfigEntry("session.disable_prepacking", "1");
            if (mode.contains("nopattern")) opts.setMemoryPatternOptimization(false);
            System.out.println("mode " + mode);
            long before = rssKb(), t0 = System.nanoTime();
            OrtSession session = env.createSession(path, opts);
            System.out.printf("threads %d: loaded in %.0f ms, memory +%d MB%n", threads, (System.nanoTime() - t0) / 1e6, (rssKb() - before) / 1024);
            Random rng = new Random(1);
            if (kind.equals("ck")) {
                float[] f = new float[128];
                for (int i = 0; i < 128; i++) f[i] = rng.nextFloat();
                float[] keys = new float[128];
                boolean[] mask = new boolean[64];
                for (int k = 0; k < 26; k++) {
                    keys[2 * k] = (k % 10 + .5f) / 10f;
                    keys[2 * k + 1] = (k / 10 + .5f) / 3f;
                    mask[k] = true;
                }
                Map<String, OnnxTensor> in = new HashMap<>();
                in.put("features", OnnxTensor.createTensor(env, FloatBuffer.wrap(f), new long[]{1, 2, 64}));
                in.put("layout_keys", OnnxTensor.createTensor(env, FloatBuffer.wrap(keys), new long[]{1, 64, 2}));
                in.put("layout_mask", OnnxTensor.createTensor(env, new boolean[][]{mask}));
                System.out.println("  one stroke: " + time(() -> session.run(in).close(), 5, 40));
            } else {
                for (int tokens : new int[]{8, 32, 64, 128}) {
                    long[] ids = new long[tokens];
                    for (int i = 0; i < tokens; i++) ids[i] = 100 + rng.nextInt(20000);
                    Map<String, OnnxTensor> in = new HashMap<>();
                    in.put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(ids), new long[]{1, tokens}));
                    System.out.println("  " + tokens + " tokens of context: " + time(() -> session.run(in).close(), 2, 12));
                }
                System.out.println("  memory after the runs +" + (rssKb() - before) / 1024 + " MB");
            }
            session.close();
        }
    }
}
