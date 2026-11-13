package android.graphics;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/**
 * A stand-in for Android's Canvas, with only what GuideDecor uses. With an image it draws with Java2D; with none it only counts
 * the calls, so the frame-time check measures GuideDecor's own work and not Java2D's rasteriser.
 */
public class Canvas {
    private final ArrayDeque<Object[]> saved = new ArrayDeque<>();
    private BufferedImage image;
    private Graphics2D g;
    /** Draw calls since the canvas was made. */
    public int calls;

    public Canvas(BufferedImage image) {
        this.image = image;
        g = start(image);
    }

    /** A canvas that draws nothing. */
    public Canvas() {}

    private static Graphics2D start(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return graphics;
    }

    public int save() {
        saved.push(new Object[] { g == null ? null : g.getTransform(), g == null ? null : g.getClip(), null, null, -1 });
        return saved.size();
    }

    /** Draws into a new layer until the matching restore, which lays it down at [alpha]. The layer keeps the clip. */
    public int saveLayerAlpha(RectF bounds, int alpha) {
        if (g == null) {
            saved.push(new Object[] { null, null, null, null, alpha });
            return saved.size();
        }
        AffineTransform transform = g.getTransform();
        Shape clip = g.getClip();
        saved.push(new Object[] { transform, clip, image, g, alpha });
        image = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        g = start(image);
        g.setTransform(transform);
        g.setClip(clip);
        return saved.size();
    }

    public void restore() {
        Object[] state = saved.pop();
        if (g == null) return;
        if (state[2] != null) {
            BufferedImage layer = image;
            g.dispose();
            image = (BufferedImage) state[2];
            g = (Graphics2D) state[3];
            g.setTransform(new AffineTransform());
            g.setClip(null);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ((Integer) state[4]) / 255f));
            g.drawImage(layer, 0, 0, null);
            g.setComposite(AlphaComposite.SrcOver);
        }
        g.setTransform((AffineTransform) state[0]);
        g.setClip((Shape) state[1]);
    }

    public void translate(float dx, float dy) { if (g != null) g.translate(dx, dy); }

    public void rotate(float degrees) { if (g != null) g.rotate(Math.toRadians(degrees)); }

    public void rotate(float degrees, float px, float py) { if (g != null) g.rotate(Math.toRadians(degrees), px, py); }

    public void scale(float sx, float sy) { if (g != null) g.scale(sx, sy); }

    public void scale(float sx, float sy, float px, float py) {
        if (g == null) return;
        g.translate(px, py);
        g.scale(sx, sy);
        g.translate(-px, -py);
    }

    public boolean clipRect(RectF r) { return clipRect(r.left, r.top, r.right, r.bottom); }

    public boolean clipRect(float left, float top, float right, float bottom) {
        calls++;
        if (g != null) g.clip(new Rectangle2D.Float(left, top, right - left, bottom - top));
        return true;
    }

    public boolean clipOutRect(float left, float top, float right, float bottom) {
        calls++;
        if (g == null) return true;
        Shape clip = g.getClip();
        Area area = new Area(clip != null ? clip : new Rectangle2D.Float(-1e5f, -1e5f, 2e5f, 2e5f));
        area.subtract(new Area(new Rectangle2D.Float(left, top, right - left, bottom - top)));
        g.setClip(area);
        return true;
    }

    public boolean clipOutPath(Path path) {
        calls++;
        if (g == null) return true;
        Shape clip = g.getClip();
        Area area = new Area(clip != null ? clip : new Rectangle2D.Float(-1e5f, -1e5f, 2e5f, 2e5f));
        area.subtract(new Area(path.shape));
        g.setClip(area);
        return true;
    }

    private void shape(Shape shape, Paint paint) {
        calls++;
        if (g == null) return;
        g.setPaint(new Color(paint.colour, true));
        if (paint.style == Paint.Style.FILL || paint.style == Paint.Style.FILL_AND_STROKE) g.fill(shape);
        if (paint.style == Paint.Style.STROKE || paint.style == Paint.Style.FILL_AND_STROKE) {
            g.setStroke(stroke(paint));
            g.draw(shape);
        }
    }

    private static BasicStroke stroke(Paint paint) {
        int cap = paint.cap == Paint.Cap.ROUND ? BasicStroke.CAP_ROUND : paint.cap == Paint.Cap.SQUARE ? BasicStroke.CAP_SQUARE : BasicStroke.CAP_BUTT;
        int join = paint.join == Paint.Join.ROUND ? BasicStroke.JOIN_ROUND : paint.join == Paint.Join.BEVEL ? BasicStroke.JOIN_BEVEL : BasicStroke.JOIN_MITER;
        return new BasicStroke(Math.max(paint.strokeWidth, 0.01f), cap, join);
    }

    public void drawPath(Path path, Paint paint) { shape(path.shape, paint); }

    public void drawCircle(float x, float y, float radius, Paint paint) {
        shape(new Ellipse2D.Float(x - radius, y - radius, radius * 2, radius * 2), paint);
    }

    public void drawLine(float x0, float y0, float x1, float y1, Paint paint) {
        calls++;
        if (g == null) return;
        g.setPaint(new Color(paint.colour, true));
        g.setStroke(stroke(paint));
        g.draw(new Line2D.Float(x0, y0, x1, y1));
    }

    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        shape(new Rectangle2D.Float(left, top, right - left, bottom - top), paint);
    }

    public void drawText(String text, float x, float y, Paint paint) {
        if (g == null) {
            calls++;
            return;
        }
        java.awt.Font font = (paint.typeface != null ? paint.typeface.font : new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 1))
            .deriveFont(paint.textSize);
        GlyphVector glyphs = font.createGlyphVector(g.getFontRenderContext(), text);
        float width = (float) glyphs.getLogicalBounds().getWidth();
        float shift = paint.align == Paint.Align.CENTER ? -width / 2 : paint.align == Paint.Align.RIGHT ? -width : 0;
        shape(glyphs.getOutline(x + shift, y), paint);
    }
}
