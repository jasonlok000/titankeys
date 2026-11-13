package android.graphics;

/** A stand-in for Android's RectF. */
public class RectF {
    public float left, top, right, bottom;

    public RectF() {}

    public RectF(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public RectF(RectF r) { this(r.left, r.top, r.right, r.bottom); }

    public final float width() { return right - left; }
    public final float height() { return bottom - top; }
    public final float centerX() { return (left + right) / 2; }
    public final float centerY() { return (top + bottom) / 2; }

    public void set(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public void set(RectF r) { set(r.left, r.top, r.right, r.bottom); }
}
