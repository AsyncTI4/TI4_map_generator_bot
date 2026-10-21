package ti4.image;

import java.awt.Graphics2D;
import java.awt.Point;
import ti4.helpers.DisplayType;
import ti4.image.playerarea.PlayerAreaGenerator;

final class PlayerAreasSection {

    private PlayerAreasSection() {}

    static int draw(RenderContext ctx, Graphics2D g2, int left, int y) {
        DisplayType basicType = ctx.basicType();
        if (basicType != DisplayType.all && basicType != DisplayType.stats) {
            return y;
        }
        Point topLeft = new Point(BoardLayout.PLAYER_AREAS_LEFT + left, y);
        return new PlayerAreaGenerator(
                        g2,
                        ctx.game(),
                        ctx.fog().isPrivate(),
                        ctx.fog().viewer(),
                        ctx.overlays(),
                        ctx.canvas().playerAreasWidth(),
                        ctx.scoreTokenSpacing(),
                        ctx.neighbours())
                .drawAllPlayerAreas(topLeft);
    }
}
