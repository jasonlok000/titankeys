package android.graphics;

/** A stand-in for Android's RectF. */
public class RectF {
    public float left, top, right, bottom;

    public void set(float left, float top, float right, float bottom) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }
}
