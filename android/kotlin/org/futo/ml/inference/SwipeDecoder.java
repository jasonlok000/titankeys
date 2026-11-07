package org.futo.ml.inference;

import com.atlas.engine.Swipe;

/** Java binding to the guarded FUTO JNI entry points in the native adapter. */
public final class SwipeDecoder implements AutoCloseable {
    public static final class Result {
        public final String word;
        public final float score, ctcScore, lmScore;
        public Result(String word, float score, float ctcScore, float lmScore) {
            this.word = word; this.score = score; this.ctcScore = ctcScore; this.lmScore = lmScore;
        }
    }
    public static final class SwipeSeg {
        public final float[] x, y, t;
        public SwipeSeg(float[] x, float[] y, float[] t) { this.x = x; this.y = y; this.t = t; }
    }
    private long handle;
    static { System.loadLibrary("swipe_jni"); System.loadLibrary("personal_dictionary"); }
    public SwipeDecoder(String directory) {
        handle = nativeInitSafe(directory + "/honorable_sturgeon/model_fp32.pte", directory + "/magic_macaw/model_fp32.pte",
                1, 100, 3, true, "f", directory + "/hungry_jellyfish/context_lm.pte", directory + "/hungry_jellyfish/vocab.txt");
        if (handle == 0) throw new IllegalStateException("Could not initialise FUTO Swipe");
    }
    public void dictionaries(String letters, float[] x, float[] y, long[] tries) {
        requireOpen();
        if (letters == null || x == null || y == null || tries == null || tries.length == 0
                || letters.length() != x.length || x.length != y.length)
            throw new IllegalArgumentException("Invalid dictionary layout");
        for (int i = 0; i < x.length; i++)
            if (!Float.isFinite(x[i]) || !Float.isFinite(y[i])) throw new IllegalArgumentException("Invalid key coordinates");
        for (long pointer : tries) if (pointer == 0) throw new IllegalArgumentException("Dictionary closed");
        if (!nativeSetModeSafe(handle, letters, x, y, tries, null, null, null)) throw new IllegalStateException("Could not configure FUTO dictionaries");
    }
    public Result[] recognise(float[] x, float[] y, float[] time, String[] context, float[] weights) {
        requireOpen();
        Swipe.validate(x, y, time);
        if (context == null || weights == null) throw new IllegalArgumentException("Missing recognition context");
        for (String word : context) if (word == null) throw new IllegalArgumentException("Invalid recognition context");
        for (float weight : weights) if (!Float.isFinite(weight)) throw new IllegalArgumentException("Invalid dictionary weight");
        Result[] result = nativeRecognizeSafe(handle, new SwipeSeg[]{new SwipeSeg(x, y, time)}, new SwipeSeg[0], 16, context, 100, weights);
        return result == null ? new Result[0] : result;
    }
    public float contextWeight() { return scoring()[3]; }
    public float shapeNormalizer() { return scoring()[0]; }
    private void requireOpen() { if (handle == 0) throw new IllegalStateException("Swipe decoder closed"); }
    private float[] scoring() {
        requireOpen();
        float[] scores = nativeGetScoringSafe(handle);
        if (scores == null || scores.length < 4 || !Float.isFinite(scores[0]) || !Float.isFinite(scores[3]))
            throw new IllegalStateException("Invalid swipe scoring configuration");
        return scores;
    }
    private native float[] nativeGetScoringSafe(long handle);
    @Override public void close() { if (handle != 0) { nativeDestroySafe(handle); handle = 0; } }
    private native long nativeInitSafe(String encoder, String decoder, int threads, int beamWidth, int topK, boolean expansion,
                                   String frequencyKey, String lm, String vocab);
    private native boolean nativeSetModeSafe(long handle, String letters, float[] x, float[] y, long[] tries, String decoder, String lm, String vocab);
    private native Result[] nativeRecognizeSafe(long handle, SwipeSeg[] left, SwipeSeg[] right, int topK, String[] context, int beamWidth, float[] weights);
    private native void nativeDestroySafe(long handle);
}
