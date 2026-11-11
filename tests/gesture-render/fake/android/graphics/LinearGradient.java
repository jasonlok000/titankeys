package android.graphics;

/** A stand-in for Android's LinearGradient: two colours along a line, clamped at its ends. */
public class LinearGradient extends Shader {
    final float x0, y0, x1, y1;
    final int colour0, colour1;

    public LinearGradient(float x0, float y0, float x1, float y1, int colour0, int colour1, Shader.TileMode tile) {
        this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
        this.colour0 = colour0; this.colour1 = colour1;
    }
}
