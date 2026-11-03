package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import ti4.image.draw.ShapeDrawing;

record PlayerAreaFrame(Rectangle bounds, Color mainColor, Color accentColor, Stroke stroke) {

    static PlayerAreaFrame unframed(Rectangle bounds) {
        return new PlayerAreaFrame(bounds, null, null, null);
    }

    void draw(Graphics2D g2, int height) {
        if (stroke == null) {
            return;
        }
        g2.setStroke(stroke);
        ShapeDrawing.gradientOutline(g2, mainColor, accentColor, bounds.x, bounds.y, bounds.width, height);
    }
}
