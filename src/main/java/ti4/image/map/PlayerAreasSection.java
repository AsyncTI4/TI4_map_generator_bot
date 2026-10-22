package ti4.image.map;

import java.awt.Graphics2D;
import java.awt.Point;
import ti4.helpers.DisplayType;
import ti4.image.playerarea.PlayerAreaContext;
import ti4.image.playerarea.PlayerAreas;

final class PlayerAreasSection {

    private PlayerAreasSection() {}

    static int draw(RenderContext ctx, Graphics2D g2, int left, int y) {
        DisplayType basicType = ctx.basicType();
        if (basicType != DisplayType.all && basicType != DisplayType.stats) {
            return y;
        }
        Point topLeft = new Point(BoardLayout.PLAYER_AREAS_LEFT + left, y);
        PlayerAreaContext areaContext =
                PlayerAreaContext.of(g2, ctx.game(), ctx.fog().fogViewer(), ctx.overlays(), ctx.neighbours());
        int bottom = PlayerAreas.drawAll(areaContext, topLeft, ctx.canvas().playerAreasWidth());
        ctx.replacePlayerAreaWarnings(areaContext.warnings());
        return bottom;
    }
}
