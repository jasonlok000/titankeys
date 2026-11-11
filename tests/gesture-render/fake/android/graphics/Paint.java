package android.graphics;

/** A stand-in for Android's Paint, with only what the strip's gesture drawing uses. */
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Cap { BUTT, ROUND, SQUARE }
    public enum Join { MITER, ROUND, BEVEL }

    int colour = 0xFF000000;
    float strokeWidth = 0f;
    Style style = Style.FILL;
    Shader shader;

    public Paint(int flags) {}
    public void setColor(int colour) { this.colour = colour; }
    public int getColor() { return colour; }
    public void setAlpha(int alpha) { colour = (colour & 0x00FFFFFF) | ((alpha & 0xFF) << 24); }
    public int getAlpha() { return colour >>> 24; }
    public void setStrokeWidth(float width) { strokeWidth = width; }
    public float getStrokeWidth() { return strokeWidth; }
    public void setStyle(Style style) { this.style = style; }
    public Style getStyle() { return style; }
    Cap cap = Cap.BUTT;
    Join join = Join.MITER;
    public void setStrokeCap(Cap cap) { this.cap = cap; }
    public Cap getStrokeCap() { return cap; }
    public void setStrokeJoin(Join join) { this.join = join; }
    public Join getStrokeJoin() { return join; }
    public Shader setShader(Shader shader) { this.shader = shader; return shader; }
    public Shader getShader() { return shader; }
}
