package ti4.image;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;

public record MapFrame(int offsetX, int offsetY, int width, int height) {

    private static final String GM_FRAME_KEY = "fowMapFrame";
    private static final Set<String> CORNER_POSITIONS = Set.of("tl", "tr", "bl", "br");

    @Nullable
    static MapFrame around(
            Game game, Collection<String> positions, int fractureYbump, int padX, int padY, int minWidth) {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (String position : positions) {
            if (position == null || CORNER_POSITIONS.contains(position.toLowerCase())) continue;
            Point raw = PositionMapper.getTilePosition(position);
            if (raw == null) continue;
            Point scaled = PositionMapper.getScaledTilePosition(game, position, raw.x, raw.y, fractureYbump);
            left = Math.min(left, scaled.x + padX);
            top = Math.min(top, scaled.y + padY);
            right = Math.max(right, scaled.x + padX + TileGenerator.TILE_WIDTH);
            bottom = Math.max(bottom, scaled.y + padY + TileGenerator.TILE_HEIGHT);
        }
        if (left == Integer.MAX_VALUE) return null;

        int contentWidth = right - left + 2 * padX;
        int width = Math.max(minWidth, contentWidth);
        int offsetX = left - padX - (width - contentWidth) / 2;
        int offsetY = top - padY;
        return new MapFrame(offsetX, offsetY, width, bottom - top + 2 * padY);
    }

    public static void setGmFrame(Game game, String centre, int radius) {
        game.setStoredValue(GM_FRAME_KEY, centre + ":" + radius);
    }

    public static void clearGmFrame(Game game) {
        game.removeStoredValue(GM_FRAME_KEY);
    }

    @Nullable
    static Set<String> gmFramePositions(Game game) {
        String stored = game.getStoredValue(GM_FRAME_KEY);
        String centre = StringUtils.substringBefore(stored, ":");
        int radius = parseRadius(StringUtils.substringAfter(stored, ":"));
        if (StringUtils.isBlank(centre) || radius < 0 || PositionMapper.getTilePosition(centre) == null) {
            return null;
        }
        return positionsWithin(centre, radius);
    }

    static Set<String> positionsWithin(String centre, int radius) {
        Set<String> reached = new HashSet<>(Set.of(centre));
        Deque<String> frontier = new ArrayDeque<>(reached);
        for (int step = 0; step < radius; step++) {
            Deque<String> next = new ArrayDeque<>();
            for (String position : frontier) {
                for (String adjacent : PositionMapper.getAdjacentTilePositions(position)) {
                    if (PositionMapper.getTilePosition(adjacent) != null && reached.add(adjacent)) {
                        next.add(adjacent);
                    }
                }
            }
            frontier = next;
        }
        return reached;
    }

    private static int parseRadius(String radius) {
        try {
            return Integer.parseInt(radius.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
