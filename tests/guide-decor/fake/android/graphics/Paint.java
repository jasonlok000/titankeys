package android.graphics;

/** A stand-in for Android's Paint, with only what GuideDecor uses. */
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Cap { BUTT, ROUND, SQUARE }
    public enum Join { MITER, ROUND, BEVEL }
    public enum Align { LEFT, CENTER, RIGHT }

    int colour = 0xFF000000;
    float strokeWidth = 0f;
    Style style = Style.FILL;
    Cap cap = Cap.BUTT;
    Join join = Join.MITER;
    Align align = Align.LEFT;
    float textSize = 12f;
    Typeface typeface;

    public Paint(int flags) {}
    public void setColor(int colour) { this.colour = colour; }
    public int getColor() { return colour; }
    public void setAlpha(int alpha) { colour = (colour & 0x00FFFFFF) | ((alpha & 0xFF) << 24); }
    public int getAlpha() { return colour >>> 24; }
    public void setStrokeWidth(float width) { strokeWidth = width; }
    public float getStrokeWidth() { return strokeWidth; }
    public void setStyle(Style style) { this.style = style; }
    public Style getStyle() { return style; }
    public void setStrokeCap(Cap cap) { this.cap = cap; }
    public Cap getStrokeCap() { return cap; }
    public void setStrokeJoin(Join join) { this.join = join; }
    public Join getStrokeJoin() { return join; }
    public void setTextAlign(Align align) { this.align = align; }
    public Align getTextAlign() { return align; }
    public void setTextSize(float size) { textSize = size; }
    public float getTextSize() { return textSize; }
    public Typeface setTypeface(Typeface typeface) { this.typeface = typeface; return typeface; }
    public Typeface getTypeface() { return typeface; }
}
