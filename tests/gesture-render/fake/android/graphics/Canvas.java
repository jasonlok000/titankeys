package android.graphics;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/** A stand-in for Android's Canvas that draws with Java2D into an image, so the strip's drawing can be looked at on a computer. */
public class Canvas {
    private final ArrayDeque<Object[]> saved = new ArrayDeque<>();
    private BufferedImage image;
    private Graphics2D g;

    public Canvas(BufferedImage image) {
        this.image = image;
        g = start(image);
    }

    private static Graphics2D start(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return graphics;
    }

    public int save() {
        saved.push(new Object[] { g.getTransform(), null, null, -1 });
        return saved.size();
    }

    /** Draws into a new layer until the matching restore, which lays it down at [alpha]. */
    public int saveLayerAlpha(float left, float top, float right, float bottom, int alpha) {
        AffineTransform transform = g.getTransform();
        saved.push(new Object[] { transform, image, g, alpha });
        image = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        g = start(image);
        g.setTransform(transform);
        return saved.size();
    }

    public void restore() {
        Object[] state = saved.pop();
        if (state[1] != null) {
            BufferedImage layer = image;
            g.dispose();
            image = (BufferedImage) state[1];
            g = (Graphics2D) state[2];
            AffineTransform transform = g.getTransform();
            g.setTransform(new AffineTransform());
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ((Integer) state[3]) / 255f));
            g.drawImage(layer, 0, 0, null);
            g.setComposite(AlphaComposite.SrcOver);
            g.setTransform(transform);
        }
        g.setTransform((AffineTransform) state[0]);
    }

    public void scale(float sx, float sy, float px, float py) {
        g.translate(px, py);
        g.scale(sx, sy);
        g.translate(-px, -py);
    }

    private static Color colour(int argb) {
        return new Color(argb, true);
    }

    private void paint(Paint paint) {
        if (paint.shader instanceof LinearGradient) {
            LinearGradient s = (LinearGradient) paint.shader;
            int alpha = paint.getAlpha();
            g.setPaint(new GradientPaint(s.x0, s.y0, colour(scaled(s.colour0, alpha)), s.x1, s.y1, colour(scaled(s.colour1, alpha)), false));
        } else g.setPaint(colour(paint.colour));
    }

    private static int scaled(int argb, int alpha) {
        int a = (argb >>> 24) * alpha / 255;
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    private void shape(java.awt.Shape shape, Paint paint) {
        paint(paint);
        if (paint.style == Paint.Style.STROKE) {
            g.setStroke(new BasicStroke(paint.strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(shape);
        } else g.fill(shape);
    }

    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        shape(new Rectangle2D.Float(left, top, right - left, bottom - top), paint);
    }

    public void drawRoundRect(RectF r, float rx, float ry, Paint paint) {
        shape(new RoundRectangle2D.Float(r.left, r.top, r.right - r.left, r.bottom - r.top, rx * 2, ry * 2), paint);
    }

    public void drawOval(RectF r, Paint paint) {
        shape(new Ellipse2D.Float(r.left, r.top, r.right - r.left, r.bottom - r.top), paint);
    }

    public void drawCircle(float x, float y, float radius, Paint paint) {
        shape(new Ellipse2D.Float(x - radius, y - radius, radius * 2, radius * 2), paint);
    }

    public void drawLine(float x0, float y0, float x1, float y1, Paint paint) {
        paint(paint);
        g.setStroke(new BasicStroke(paint.strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(x0, y0, x1, y1));
    }
}
