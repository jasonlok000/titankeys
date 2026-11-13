package android.graphics;

/** A stand-in for Android's Typeface: a Java2D font, read from the app's own font file by the render. */
public class Typeface {
    public final java.awt.Font font;

    public Typeface(java.awt.Font font) { this.font = font; }
}
