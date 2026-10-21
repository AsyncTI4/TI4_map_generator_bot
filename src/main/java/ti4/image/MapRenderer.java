package ti4.image;

import java.awt.image.BufferedImage;
import ti4.helpers.DisplayType;

final class MapRenderer {

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
        try (MapGenerator generator = new MapGenerator(ctx, timings)) {
            return generator.drawCanvas();
        }
    }
}
