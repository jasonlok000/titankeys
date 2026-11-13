package android.graphics;

/** A stand-in for Android's PorterDuffXfermode. */
public class PorterDuffXfermode extends Xfermode {
    final PorterDuff.Mode mode;

    public PorterDuffXfermode(PorterDuff.Mode mode) { this.mode = mode; }
}
