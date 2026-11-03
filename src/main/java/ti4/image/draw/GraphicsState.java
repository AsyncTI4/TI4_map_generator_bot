package ti4.image.draw;

import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;

public final class GraphicsState {

    private final RenderingHints hints;
    private final Stroke stroke;
    private final Paint paint;
    private final Composite composite;
    private final Font font;
    private final AffineTransform transform;
    private final Shape clip;
    private final Color background;

    private GraphicsState(Graphics2D g2) {
        hints = g2.getRenderingHints();
        stroke = g2.getStroke();
        paint = g2.getPaint();
        composite = g2.getComposite();
        font = g2.getFont();
        transform = g2.getTransform();
        clip = g2.getClip();
        background = g2.getBackground();
    }

    public static GraphicsState capture(Graphics2D g2) {
        return new GraphicsState(g2);
    }

    public void applyTo(Graphics2D g2) {
        g2.setRenderingHints(hints);
        g2.setStroke(stroke);
        g2.setPaint(paint);
        g2.setComposite(composite);
        g2.setFont(font);
        g2.setTransform(transform);
        g2.setClip(clip);
        g2.setBackground(background);
    }
}
