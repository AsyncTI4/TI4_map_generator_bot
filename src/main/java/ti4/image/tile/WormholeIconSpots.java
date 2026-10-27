package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.game.Tile;
import ti4.model.TileHelper;

final class WormholeIconSpots {

    private record Spot(char wormhole, int x, int y) {}

    private record TileSpots(List<String> tileIds, List<Spot> spots) {}

    private static final Map<String, List<Spot>> SPOTS_BY_TILE = Stream.of(
                    new TileSpots(List.of("17"), List.of(new Spot('d', 187, 206))),
                    new TileSpots(List.of("51"), List.of(new Spot('d', 239, 251))),
                    new TileSpots(List.of("82a"), List.of(new Spot('g', 169, 273))),
                    new TileSpots(
                            List.of("82b"),
                            List.of(new Spot('a', 95, 249), new Spot('b', 169, 273), new Spot('g', 243, 249))),
                    new TileSpots(List.of("94"), List.of(new Spot('e', 45, 152))),
                    new TileSpots(List.of("118"), List.of(new Spot('e', 250, 236))),
                    new TileSpots(List.of("c02"), List.of(new Spot('a', 37, 158), new Spot('b', 223, 62))),
                    new TileSpots(List.of("c10"), List.of(new Spot('a', 182, 22), new Spot('b', 259, 241))),
                    new TileSpots(List.of("c11"), List.of(new Spot('a', 54, 138), new Spot('b', 159, 275))),
                    new TileSpots(List.of("d119"), List.of(new Spot('b', 94, 170))),
                    new TileSpots(List.of("d123"), List.of(new Spot('a', 22, 110), new Spot('b', 190, 206))),
                    new TileSpots(List.of("er19", "er119"), List.of(new Spot('a', 60, 44), new Spot('b', 192, 184))),
                    new TileSpots(List.of("er94"), List.of(new Spot('b', 157, 165))),
                    new TileSpots(List.of("er95"), List.of(new Spot('a', 60, 155), new Spot('b', 215, 61))),
                    new TileSpots(List.of("m05"), List.of(new Spot('a', 185, 180))),
                    new TileSpots(List.of("m32"), List.of(new Spot('b', 49, 147))))
            .flatMap(entry -> entry.tileIds().stream().map(id -> Map.entry(id, entry.spots())))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    private static final int DEFAULT_X = 86;
    private static final int DEFAULT_Y = 260;

    private WormholeIconSpots() {}

    static void draw(Tile tile, Graphics2D graphics, BufferedImage icon, int offset, String wormholes, boolean all) {
        List<Spot> spots = SPOTS_BY_TILE.get(tile.getTileID());
        if (spots != null) {
            for (Spot spot : spots) {
                if (all || wormholes.indexOf(spot.wormhole()) >= 0) {
                    graphics.drawImage(icon, TILE_PADDING + offset + spot.x(), TILE_PADDING + offset + spot.y(), null);
                }
            }
            return;
        }
        Point wormholeLocation =
                TileHelper.getTileById(tile.getTileID()).getShipPositionsType().getWormholeLocation();
        if (wormholeLocation == null) {
            graphics.drawImage(icon, TILE_PADDING + offset + DEFAULT_X, TILE_PADDING + DEFAULT_Y, null);
        } else {
            graphics.drawImage(
                    icon, TILE_PADDING + offset + wormholeLocation.x, TILE_PADDING + offset + wormholeLocation.y, null);
        }
    }
}
