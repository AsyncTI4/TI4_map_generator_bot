package ti4.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;
import ti4.helpers.Helper;
import ti4.image.PositionMapper;

@Data
public class MapTemplateModel implements ModelInterface {
    @Data
    public static class MapTemplateTile {
        // This field is for when you want a specific tile on the map, in the same position every time
        // Such as Mecatol Rex in the middle, or hyperlanes for a 3,4,5,7,8 player game, etc.
        private String staticTileId;
        private Boolean custodians; //add custodian to this tile (presently only works with MR)

        // These three fields control if a particular tile is a placeholder for a milty draft tile.
        private Integer playerNumber;
        private Integer miltyTileIndex;
        private Boolean home;

        // This is the position the tile should be on the map
        private String pos;
    }

    private String alias;
    private String author;
    private String descr;
    private Integer playerCount;
    private Integer tilesPerPlayer;
    private Integer bluePerPlayer;
    private Integer redPerPlayer;
    private boolean toroidal;
    private List<String> sliceEmulateTiles; // [homePos, tile0pos, tile1pos, ...]
    private List<MapTemplateTile> templateTiles; // MECATOL REX IS NOT INCLUDED BY DEFAULT

    public boolean isValid() {
        return alias != null
            && (tileDisplayCoords().size() == (1 + tilesPerPlayer()))
            && ((bluePerPlayer() + redPerPlayer()) == tilesPerPlayer());
    }

    public String autoCompleteString() {
        return getAlias() + ": " + getPlayerCount() + " player map by " + getAuthor();
    }

    // ---------------------------------------------------------------------------------------------
    // Helper Functions
    // ---------------------------------------------------------------------------------------------
    public int tilesPerPlayer() {
        int calculated = (int) templateTiles.stream()
            .filter(t -> t.playerNumber != null && t.miltyTileIndex != null && t.playerNumber == 1)
            .count();
        return tilesPerPlayer == null ? calculated : tilesPerPlayer;
    }

    public int bluePerPlayer() {
        return bluePerPlayer == null ? 3 : bluePerPlayer;
    }

    public int redPerPlayer() {
        return redPerPlayer == null ? 2 : redPerPlayer;
    }

    public List<String> emulatedTiles() {
        List<String> emulate = getSliceEmulateTiles();
        if (emulate == null || emulate.isEmpty()) {
            emulate = List.of("310", "311", "207", "309", "208", "104");
        }
        return emulate;
    }

    public List<Point> tileDisplayCoords() {
        List<String> emulate = getSliceEmulateTiles();
        if (emulate == null || emulate.isEmpty()) {
            emulate = List.of("310", "311", "207", "309", "208", "104");
        }
        List<Point> displayCoords = new ArrayList<>();
        int minx = 10000, miny = 10000;
        for (String pos : emulate) {
            Point p = PositionMapper.getTilePosition(pos);
            if (p == null) continue;

            displayCoords.add(p);
            minx = Math.min(minx, p.x);
            miny = Math.min(miny, p.y);
        }
        for (Point p : displayCoords)
            p.translate(-1 * minx, -1 * miny);
        return displayCoords;
    }

    public int squareSliceImageSize() {
        int size = 0;
        for (Point p : tileDisplayCoords()) {
            size = Math.max(size, Math.max(p.x + 345, p.y + 300));
        }
        return size;
    }

    public int numRings() {
        String highestPosition = getTemplateTiles().stream().map(MapTemplateTile::getPos)
            .filter(Helper::isInteger)
            .max(Comparator.comparingInt(Integer::parseInt))
            .orElse(null);
        if (highestPosition == null) return 0;

        String firstTwoDigits = StringUtils.left(highestPosition, highestPosition.length() - 2);

        if (!Helper.isInteger(firstTwoDigits)) return 0;
        return Integer.parseInt(firstTwoDigits);
    }

    public List<Integer> getSortedHomeSystemLocations() {
        List<Integer> locations = new ArrayList<>();
        for (int i = 1; i <= playerCount; i++) {
            for (MapTemplateTile t : templateTiles) {
                if (i != Objects.requireNonNullElse(t.getPlayerNumber(), 0)) continue;
                if (!Objects.requireNonNullElse(t.getHome(), false)) continue;

                try {
                    locations.add(Integer.parseInt(t.getPos()));
                } catch (Exception e) {}
            }
        }
        return locations;
    }
}
