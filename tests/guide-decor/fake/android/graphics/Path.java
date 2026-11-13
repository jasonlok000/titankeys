package android.graphics;

import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/** A stand-in for Android's Path, with only what GuideDecor uses. */
public class Path {
    public enum Direction { CW, CCW }

    final Path2D.Float shape = new Path2D.Float();
    private boolean empty = true;

    public void reset() {
        shape.reset();
        empty = true;
    }

    public void moveTo(float x, float y) {
        shape.moveTo(x, y);
        empty = false;
    }

    public void lineTo(float x, float y) {
        if (empty) moveTo(0, 0);
        shape.lineTo(x, y);
    }

    public void quadTo(float x1, float y1, float x2, float y2) {
        if (empty) moveTo(0, 0);
        shape.quadTo(x1, y1, x2, y2);
    }

    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        if (empty) moveTo(0, 0);
        shape.curveTo(x1, y1, x2, y2, x3, y3);
    }

    public void close() { shape.closePath(); }

    /** Android's angles run clockwise on screen; Arc2D's run the other way, so both are negated. */
    public void arcTo(float left, float top, float right, float bottom, float startAngle, float sweepAngle, boolean forceMoveTo) {
        Arc2D.Float arc = new Arc2D.Float(left, top, right - left, bottom - top, -startAngle, -sweepAngle, Arc2D.OPEN);
        shape.append(arc, !forceMoveTo && !empty);
        empty = false;
    }

    public void addCircle(float x, float y, float radius, Direction direction) {
        shape.append(new Ellipse2D.Float(x - radius, y - radius, radius * 2, radius * 2), false);
        empty = false;
    }
}
