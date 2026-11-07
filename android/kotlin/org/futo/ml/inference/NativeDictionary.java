package org.futo.ml.inference;

public final class NativeDictionary implements AutoCloseable {
    private long handle;
    static { System.loadLibrary("swipe_jni"); System.loadLibrary("personal_dictionary"); }
    public NativeDictionary(String path) {
        if (path == null) throw new IllegalArgumentException("Missing dictionary path");
        handle = nativeLoad(path);
        if (handle == 0) throw new IllegalStateException("Could not load dictionary");
    }
    public NativeDictionary(String[] words, float[] frequencies) {
        if (words == null || frequencies == null || words.length != frequencies.length)
            throw new IllegalArgumentException("Invalid dictionary entries");
        for (int i = 0; i < words.length; i++)
            if (words[i] == null || words[i].isEmpty() || !Float.isFinite(frequencies[i]) || frequencies[i] < 0)
                throw new IllegalArgumentException("Invalid dictionary entry");
        handle = nativeCreate(words, frequencies);
        if (handle == 0) throw new IllegalStateException("Could not create dictionary");
    }
    public long pointer() { if (handle == 0) throw new IllegalStateException("Dictionary closed"); return nativePointer(handle); }
    public float frequency(String word) {
        if (handle == 0) throw new IllegalStateException("Dictionary closed");
        if (word == null) throw new IllegalArgumentException("Missing dictionary word");
        return nativeFrequency(handle, word);
    }
    @Override public void close() { if (handle != 0) { nativeDestroy(handle); handle = 0; } }
    private static native long nativeLoad(String path);
    private static native long nativeCreate(String[] words, float[] frequencies);
    private static native long nativePointer(long handle);
    private static native float nativeFrequency(long handle, String word);
    private static native void nativeDestroy(long handle);
}
