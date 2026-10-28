package ti4.image.draw;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import lombok.experimental.UtilityClass;

@UtilityClass
public class InfinitySymbol {

    public static void draw(Graphics2D g, int centerX, int centerY, int width, Color color, Color outline) {
        int loop = width / 2;
        Ellipse2D left = new Ellipse2D.Double(centerX - loop, centerY - loop / 2.0, loop, loop);
        Ellipse2D right = new Ellipse2D.Double(centerX, centerY - loop / 2.0, loop, loop);
        Object antialias = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke stroke = g.getStroke();
        Color previous = g.getColor();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float line = Math.max(2f, width / 10f);
        g.setStroke(new BasicStroke(line + 4f));
        g.setColor(outline);
        g.draw(left);
        g.draw(right);
        g.setStroke(new BasicStroke(line));
        g.setColor(color);
        g.draw(left);
        g.draw(right);
        g.setColor(previous);
        g.setStroke(stroke);
        if (antialias != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
    }
}
