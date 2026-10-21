package ti4.image.map;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import ti4.helpers.DisplayType;
import ti4.image.draw.GraphicsState;
import ti4.image.io.EncodedImage;
import ti4.image.io.ImageEncoder;
import ti4.service.fow.FogMemoryService;
import ti4.service.fow.FogViewer;

final class MapRenderer {

    private static final int SCORE_TRACK_TOP_MARGIN = 20;

    private MapRenderer() {}

    static RenderContext resolve(MapRenderRequest request) {
        FogViewer viewer = FogViewer.resolve(request.game(), request.event(), null);
        FogMemoryService.refresh(request.game(), viewer);
        return RenderContext.build(request, viewer);
    }

    static MapRenderResult render(MapRenderRequest request, RenderTimings timings) {
        return render(resolve(request), timings);
    }

    static MapRenderResult render(RenderContext ctx, RenderTimings timings) {
        timings.startDraw();
        BufferedImage canvas = draw(ctx, timings);
        EncodedImage encoded = ImageEncoder.forDiscord(canvas);
        timings.stopGraphics();
        timings.stopDraw();
        boolean eligibleForWebsite =
                ctx.basicType() == DisplayType.all && !ctx.fog().isPrivate();
        return new MapRenderResult(
                canvas, encoded, ctx.overlays().asList(), eligibleForWebsite, ctx.playerAreaWarnings());
    }

    static BufferedImage draw(RenderContext ctx, RenderTimings timings) {
        BufferedImage canvas = paint(ctx, timings);
        clearShownDistances(ctx);
        return canvas;
    }

    private static void clearShownDistances(RenderContext ctx) {
        if (BoardSection.drawsTiles(ctx)) {
            ctx.game().setTileDistances(new HashMap<>());
        }
    }

    static BufferedImage paint(RenderContext ctx, RenderTimings timings) {
        return paint(ctx, timings, ctx.canvas().allocatedHeight());
    }

    static BufferedImage paint(RenderContext ctx, RenderTimings timings, int allocatedHeight) {
        return paint(ctx, timings, allocatedHeight, true);
    }

    private static BufferedImage paint(
            RenderContext ctx, RenderTimings timings, int allocatedHeight, boolean mayPaintAgain) {
        MapCanvasLayout layout = ctx.canvas();
        BufferedImage canvas = new BufferedImage(layout.width(), allocatedHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = canvas.createGraphics();
        int playerAreasTop;
        int bottom;
        GraphicsState stateBeforePlayerAreas;
        int overlaysBeforePlayerAreas;
        try {
            timings.startTiles();
            BoardSection.draw(ctx, g2);
            ConnectionStripSection.draw(ctx, g2);
            timings.stopTiles();

            timings.startGraphics();
            playerAreasTop = drawInfoArea(ctx, g2);
            stateBeforePlayerAreas = GraphicsState.capture(g2);
            overlaysBeforePlayerAreas = ctx.overlays().size();
            bottom = PlayerAreasSection.draw(ctx, g2, layout.landscapeShift(), playerAreasTop);
        } finally {
            g2.dispose();
        }
        if (!layout.heightFollowsContent()) {
            return canvas;
        }
        int height = layout.heightFor(drawnBottom(canvas, bottom));
        if (height > allocatedHeight && !mayPaintAgain) {
            return canvas;
        }
        if (height > allocatedHeight && (layout.isLandscape() || playerAreasTop >= allocatedHeight)) {
            ctx.overlays().truncateTo(0);
            return paint(ctx, timings, height, false);
        }
        if (height > allocatedHeight) {
            BufferedImage infoArea = copyOfTopRows(canvas, playerAreasTop);
            canvas = null;
            return redrawPlayerAreasOnTallerCanvas(
                    ctx, infoArea, playerAreasTop, height, stateBeforePlayerAreas, overlaysBeforePlayerAreas);
        }
        return height < allocatedHeight ? canvas.getSubimage(0, 0, canvas.getWidth(), height) : canvas;
    }

    private static int drawnBottom(BufferedImage canvas, int playerAreasBottom) {
        if (playerAreasBottom >= canvas.getHeight()) {
            return playerAreasBottom;
        }
        return CanvasBottom.ofDrawnContent(canvas, playerAreasBottom);
    }

    private static BufferedImage copyOfTopRows(BufferedImage canvas, int rows) {
        BufferedImage copy = new BufferedImage(canvas.getWidth(), rows, BufferedImage.TYPE_INT_RGB);
        copy.getRaster().setRect(canvas.getRaster().createChild(0, 0, canvas.getWidth(), rows, 0, 0, null));
        return copy;
    }

    private static BufferedImage redrawPlayerAreasOnTallerCanvas(
            RenderContext ctx,
            BufferedImage infoArea,
            int playerAreasTop,
            int height,
            GraphicsState stateBeforePlayerAreas,
            int overlaysBeforePlayerAreas) {
        BufferedImage canvas = new BufferedImage(infoArea.getWidth(), height, BufferedImage.TYPE_INT_RGB);
        canvas.getRaster().setRect(infoArea.getRaster());
        ctx.overlays().truncateTo(overlaysBeforePlayerAreas);
        Graphics2D g2 = canvas.createGraphics();
        try {
            stateBeforePlayerAreas.applyTo(g2);
            PlayerAreasSection.draw(ctx, g2, ctx.canvas().landscapeShift(), playerAreasTop);
        } finally {
            g2.dispose();
        }
        return canvas;
    }

    private static int drawInfoArea(RenderContext ctx, Graphics2D g2) {
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
        return y;
    }
}
