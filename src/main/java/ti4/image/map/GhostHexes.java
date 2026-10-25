package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ti4.game.Player;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.image.tile.TileGeometry;
import ti4.model.MapPositionMapper;
import ti4.service.option.FOWOptionService.FOWOption;

final class GhostHexes {

    private static final Color RING = new Color(255, 255, 255, 128);
    private static final Color NUMBER = new Color(255, 255, 255, 150);
    private static final Color NUMBER_OUTLINE = new Color(0, 0, 0, 120);

    private GhostHexes() {}

    static void draw(RenderContext ctx, Graphics2D g2, Set<String> drawnPositions) {
        BoardLayout board = ctx.board();
        for (String position : frontierPositions(ctx)) {
            Rectangle hex = board.hexBounds(position);
            if (hex != null
                    && !board.isOutsideFrame(hex.x - TileGeometry.TILE_PADDING, hex.y - TileGeometry.TILE_PADDING)) {
                drawHex(g2, position, hex, !drawnPositions.contains(position));
            }
        }
    }

    static Set<String> frontier(Collection<String> sources, Set<String> known) {
        Set<String> frontier = new HashSet<>();
        for (String source : sources) {
            for (String neighbour : MapPositionMapper.getAdjacentTilePositions(source)) {
                if (!known.contains(neighbour) && MapPositionMapper.isTilePositionValid(neighbour)) {
                    frontier.add(neighbour);
                }
            }
        }
        return frontier;
    }

    private static Set<String> frontierPositions(RenderContext ctx) {
        FogView fog = ctx.fog();
        Player viewer = fog.viewer();
        if (!fog.isPrivate()
                || viewer == null
                || !viewer.isFogGhostHexes()
                || !ctx.game().getFowOption(FOWOption.GHOST_HEXES)) {
            return Set.of();
        }
        BoardLayout board = ctx.board();
        Set<String> known = fog.seenPositions();
        List<String> sources = known.stream()
                .filter(position -> board.isInShownRegion(position)
                        && !board.pinnedCorners().contains(position))
                .toList();
        return frontier(sources, known);
    }

    private static void drawHex(Graphics2D g2, String position, Rectangle hex, boolean withNumber) {
        Stroke previousStroke = g2.getStroke();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(RING);
        g2.setStroke(Strokes.STROKE_3);
        g2.drawPolygon(MapFrame.hexPolygon(hex));
        g2.setStroke(previousStroke);
        if (withNumber) {
            g2.setFont(Fonts.ti(40));
            OutlinedText.draw(
                    g2,
                    position,
                    (int) hex.getCenterX(),
                    (int) hex.getCenterY(),
                    NUMBER,
                    HorizontalAlign.Center,
                    VerticalAlign.Center,
                    Strokes.STROKE_3,
                    NUMBER_OUTLINE);
        }
    }
}
