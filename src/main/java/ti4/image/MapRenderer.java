package ti4.image;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import ti4.helpers.DisplayType;

final class MapRenderer {

    private static final int SCORE_TRACK_TOP_MARGIN = 20;

    private MapRenderer() {}

    static RenderContext resolve(MapRenderRequest request) {
        return RenderContext.build(request);
    }

    static MapRenderResult render(MapRenderRequest request, RenderTimings timings) {
        return render(resolve(request), timings);
    }

    static MapRenderResult render(RenderContext ctx, RenderTimings timings) {
        timings.startDraw();
        BufferedImage canvas = paint(ctx, timings);
        EncodedImage encoded = MapImageEncoder.encode(canvas);
        timings.stopGraphics();
        timings.stopDraw();
        boolean eligibleForWebsite =
                ctx.basicType() == DisplayType.all && !ctx.fog().isPrivate();
        return new MapRenderResult(canvas, encoded, ctx.overlays().asList(), eligibleForWebsite);
    }

    static BufferedImage paint(RenderContext ctx, RenderTimings timings) {
        MapCanvasLayout layout = ctx.canvas();
        BufferedImage canvas = new BufferedImage(layout.width(), layout.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = canvas.createGraphics();
        try {
            timings.startTiles();
            BoardSection.draw(ctx, g2);
            ConnectionStripSection.draw(ctx, g2);
            timings.stopTiles();

            timings.startGraphics();
            drawInfoArea(ctx, g2);
        } finally {
            g2.dispose();
        }
        return canvas;
    }

    private static void drawInfoArea(RenderContext ctx, Graphics2D g2) {
        int left = ctx.canvas().landscapeShift();
        int y = HeaderSection.draw(ctx, g2, left);
        y = ScoreTrackSection.draw(ctx, g2, left, y + SCORE_TRACK_TOP_MARGIN);
        if (PriorityTrackSection.isShown(ctx.game())) {
            y = PriorityTrackSection.draw(ctx, g2, left, y);
        }
        y = ObjectivesSection.draw(ctx, g2, left, y);
        y = LawsAndEventsSection.drawLaws(ctx, g2, left, y);
        y = LawsAndEventsSection.drawEvents(ctx, g2, left, y);
        if (MapSideStatsSection.isDrawn(ctx)) {
            MapSideStatsSection.draw(ctx, g2);
        }
        PlayerAreasSection.draw(ctx, g2, left, y);
    }
}
