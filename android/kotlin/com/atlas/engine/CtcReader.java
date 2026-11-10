package com.atlas.engine;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/** Reads a swipe with the CleverKeys encoder, fine-tuned on Titan strokes, and finds words with {@link CtcWords}. */
public final class CtcReader implements AutoCloseable {
    private final OrtEnvironment env;
    private final OrtSession session;
    private final CtcWords words;
    private final OnnxTensor keys, mask;

    public CtcReader(File model, CtcWords words, int threads) throws OrtException {
        this.words = words;
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(threads);
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        session = env.createSession(model.getPath(), options);
        keys = OnnxTensor.createTensor(env, FloatBuffer.wrap(CtcWords.titanKeys()), new long[]{1, 64, 2});
        boolean[] letters = new boolean[64];
        for (int i = 0; i < 26; i++) letters[i] = true;
        mask = OnnxTensor.createTensor(env, new boolean[][]{letters});
    }

    public CtcWords words() { return words; }

    public CtcWords.Candidate[] read(Swipe stroke) throws OrtException {
        float[] features = CtcWords.features(stroke.x, stroke.y, stroke.time);
        Map<String, OnnxTensor> in = new HashMap<>();
        try (OnnxTensor f = OnnxTensor.createTensor(env, FloatBuffer.wrap(features), new long[]{1, 2, 64})) {
            in.put("features", f);
            in.put("layout_keys", keys);
            in.put("layout_mask", mask);
            try (OrtSession.Result result = session.run(in)) {
                float[][][] emissions = (float[][][]) result.get(0).getValue();
                int frames = emissions[0].length, width = emissions[0][0].length;
                float[] flat = new float[frames * width];
                for (int t = 0; t < frames; t++) System.arraycopy(emissions[0][t], 0, flat, t * width, width);
                return words.search(CtcWords.letterChances(flat, frames, width));
            }
        }
    }

    @Override
    public void close() {
        try { session.close(); } catch (OrtException ignored) { }
        keys.close();
        mask.close();
    }
}
