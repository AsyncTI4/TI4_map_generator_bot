package ti4.model;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.SerializationUtils;
import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.model.ShipPositionModel.ShipPosition;

// Handles positions of map
public final class MapPositionMapper {
    private static final int RING_MAX_COUNT = 8;
    private static final int RING_MIN_COUNT = 3;
    private static final int HORIZONTAL_TILE_SPACING = 260;
    private static final int EXTRA_BOARD_STRIDE = boardWidth(BoardPosition.MAX_RING);
    private static final int SPACE_FOR_TILE_HEIGHT = 300; // space to calculate tile image height with
    private static final int TILE_WIDTH = 345;
    private static final int REFRACTURE_SIDE_TILE_GAP = 175;
    private static final int TOP_REFRACTURE_NORTH_OFFSET = 125;
    private static final int LOWER_REFRACTURE_SOUTH_OFFSET = 150;

    private static final Properties tileImageCoordinates = new Properties();
    private static final Properties playerInfo = new Properties();
    private static final Properties playerInfo8 = new Properties();
    private static final Properties playerInfo8ring = new Properties();
    private static final Properties stats = new Properties();
    private static final Properties reinforcements = new Properties();
    private static final Properties tileAdjacencies = new Properties();

    public static void init() {
        readData("tileImageCoordinates.properties", tileImageCoordinates, "Could not read position file");
        readData("6player_info.properties", playerInfo, "Could not read player info position file");
        readData("8player_info.properties", playerInfo8, "Could not read player info position file");
        readData("8ring_info.properties", playerInfo8ring, "Could not read player info position file");
        readData("stats.properties", stats, "Could not read player info position file");
        readData("reinforcements.properties", reinforcements, "Could not read reinforcements position file");
        readData("tileAdjacencies.properties", tileAdjacencies, "Could not read adjacent tiles file");
    }

    private static void readData(String fileName, Properties positionMap, String errorMessage) {
        String positionFile = ResourceHelper.getPositionFile(fileName);
        if (positionFile != null) {
            try (InputStream input = new FileInputStream(positionFile)) {
                positionMap.load(input);
            } catch (IOException e) {
                BotLogger.error(errorMessage, e);
            }
        }
    }

    public static Map<String, Point> getTilePlanetPositions(String tileID) {
        if ("nombox".equals(tileID)) {
            return null;
        }
        var tileIdToPlanets = TileHelper.getPlanetsByTileId(tileID);
        return tileIdToPlanets == null
                ? null
                : tileIdToPlanets.stream()
                        .collect(Collectors.toMap(PlanetModel::getId, PlanetModel::getPositionInTile));
    }

    public static List<Point> getSpaceTokenPositions(String tileID) {
        List<Point> backup = List.of(
                new Point(190, 30), new Point(215, 110), new Point(185, 205), new Point(100, 190), new Point(60, 130));
        TileModel tile = TileHelper.getTileById(tileID);

        return Optional.ofNullable(tile.getShipPositionsType())
                .map(ShipPosition::getSpaceTokenLayout)
                .orElse(backup);
    }

    public static boolean isTilePositionValid(Game game, String position) {
        return isTilePositionValid(position) && (game.isFowMode() || !BoardPosition.isBoardPosition(position));
    }

    public static boolean isTilePositionValid(String position) {
        return tileImageCoordinates.getProperty(position) != null
                || BoardPosition.parse(position)
                        .map(board -> tileImageCoordinates.getProperty(board.local()) != null)
                        .orElse(false);
    }

    public static Set<String> getTilePositions() {
        Set<String> positions = new HashSet<>();
        for (Object key : tileImageCoordinates.keySet()) {
            if (key instanceof String position) {
                positions.add(position);
            }
        }
        return positions;
    }

    public static Point getScaledTilePosition(Game game, String position, int x, int y) {
        return getScaledTilePosition(game, position, x, y, 0);
    }

    public static int layoutRingCount(Game game) {
        int mainRings = game.getRingCount();
        if (!game.isFowMode()) {
            return mainRings;
        }
        int boardRings = game.getTileMap().keySet().stream()
                .map(BoardPosition::parse)
                .flatMap(Optional::stream)
                .mapToInt(board -> Integer.parseInt(board.local()) / 100)
                .max()
                .orElse(0);
        return Math.max(mainRings, boardRings);
    }

    public static int boardLayoutRingCount(Game game) {
        return boardLayoutRingCount(layoutRingCount(game));
    }

    public static int boardWidth(int ringCount) {
        return (2 * ringCount + 2) * HORIZONTAL_TILE_SPACING;
    }

    private static int extraBoardOffsetX(int boardIndex) {
        return (boardIndex + 1) * EXTRA_BOARD_STRIDE;
    }

    private static int extraBoardCompression(int boardIndex, int ringCount) {
        return (boardIndex + 1) * (BoardPosition.MAX_RING - ringCount) * 2 * HORIZONTAL_TILE_SPACING;
    }

    private static int boardLayoutRingCount(int layoutRings) {
        return Math.clamp(layoutRings, RING_MIN_COUNT, BoardPosition.MAX_RING);
    }

    public static Point getScaledTilePosition(Game game, String position, int x, int y, int fractureYbump) {
        Point scaled = projectedTilePosition(game, position, x, y, fractureYbump);
        scaled.translate(
                refractureHorizontalOffset(game, fractureYbump), refractureVerticalOffset(game, fractureYbump));
        if (isTopRefractureTile(game, position)) scaled.translate(0, -TOP_REFRACTURE_NORTH_OFFSET);
        if (isLowerRefractureTile(game, position)) scaled.translate(0, LOWER_REFRACTURE_SOUTH_OFFSET);
        return scaled;
    }

    private static Point projectedTilePosition(Game game, String position, int x, int y, int fractureYbump) {
        Point scaled;
        if (isRefractureTile(game, position)) {
            scaled = dynamicallyPlaceRefractureTile(game, position, x, y, fractureYbump);
        } else {
            scaled = scaleTilePosition(game, position, x, y, fractureYbump, false);
        }
        return scaled;
    }

    private static int refractureVerticalOffset(Game game, int fractureYbump) {
        if (!game.isRefractureMode()
                || game.getTileMap().keySet().stream().noneMatch(position -> position.startsWith("frac"))) {
            return 0;
        }
        int top = game.getTileMap().keySet().stream()
                .map(position -> new PositionedPoint(position, getTilePosition(position)))
                .filter(position -> position.point() != null)
                .mapToInt(position -> projectedTilePosition(
                                game, position.position(), position.point().x, position.point().y, fractureYbump)
                        .y)
                .min()
                .orElse(0);
        return Math.max(0, 100 - top);
    }

    private static int refractureHorizontalOffset(Game game, int fractureYbump) {
        if (!game.isRefractureMode()
                || game.getTileMap().keySet().stream().noneMatch(position -> position.startsWith("frac"))) {
            return 0;
        }
        int left = game.getTileMap().keySet().stream()
                .map(position -> new PositionedPoint(position, getTilePosition(position)))
                .filter(position -> position.point() != null)
                .mapToInt(position -> projectedTilePosition(
                                game, position.position(), position.point().x, position.point().y, fractureYbump)
                        .x)
                .min()
                .orElse(0);
        return Math.max(0, 50 - left);
    }

    @Nullable
    public static Rectangle getRenderedTileBounds(Game game, int fractureYbump) {
        Rectangle bounds = null;
        for (String position : game.getTileMap().keySet()) {
            Point raw = getTilePosition(position);
            if (raw == null) continue;
            Point scaled = getScaledTilePosition(game, position, raw.x, raw.y, fractureYbump);
            Rectangle tileBounds = new Rectangle(scaled.x, scaled.y, TILE_WIDTH, SPACE_FOR_TILE_HEIGHT);
            bounds = bounds == null ? tileBounds : bounds.union(tileBounds);
        }
        return bounds;
    }

    private static Point dynamicallyPlaceRefractureTile(Game game, String position, int x, int y, int fractureYbump) {
        Point tilePosition = scaleTilePosition(game, position, x, y, fractureYbump, true);
        List<Point> refractureTiles = game.getTileMap().keySet().stream()
                .filter(candidate -> isRefractureTile(game, candidate))
                .map(candidate -> new PositionedPoint(candidate, getTilePosition(candidate)))
                .filter(candidate -> candidate.point() != null)
                .map(candidate -> scaleTilePosition(
                        game, candidate.position(), candidate.point().x, candidate.point().y, fractureYbump, true))
                .toList();
        List<Point> mapTiles = game.getTileMap().keySet().stream()
                .filter(candidate -> !candidate.startsWith("frac"))
                .map(candidate -> new PositionedPoint(candidate, getTilePosition(candidate)))
                .filter(candidate -> candidate.point() != null)
                .map(candidate -> scaleTilePosition(
                        game, candidate.position(), candidate.point().x, candidate.point().y, fractureYbump, false))
                .toList();
        if (refractureTiles.isEmpty() || mapTiles.isEmpty() || !tilesOverlap(refractureTiles, mapTiles)) {
            return tilePosition;
        }
        Point translation = minimumRefractureTranslation(game, refractureTiles, mapTiles);
        tilePosition.translate(translation.x, translation.y);
        return tilePosition;
    }

    private static Point minimumRefractureTranslation(Game game, List<Point> refractureTiles, List<Point> mapTiles) {
        List<Point> candidates = new ArrayList<>(List.of(new Point()));
        for (Point refractureTile : refractureTiles) {
            for (Point mapTile : mapTiles) {
                addRefractureTranslationCandidates(game, candidates, refractureTile, mapTile, REFRACTURE_SIDE_TILE_GAP);
            }
        }
        return candidates.stream()
                .filter(candidate -> isLayoutDirection(game, candidate))
                .filter(candidate -> !tilesOverlap(refractureTiles, mapTiles, candidate))
                .min(java.util.Comparator.comparingInt(candidate -> Math.abs(candidate.x) + Math.abs(candidate.y)))
                .orElseGet(Point::new);
    }

    private static boolean isLayoutDirection(Game game, Point candidate) {
        return switch (game.getStoredValue("refractureLayoutEdge")) {
            case "UPPER_RIGHT", "LOWER_RIGHT" -> candidate.x >= 0 && candidate.y == 0;
            case "BOTTOM" -> candidate.x == 0 && candidate.y >= 0;
            case "LOWER_LEFT", "UPPER_LEFT" -> candidate.x <= 0 && candidate.y == 0;
            default -> candidate.x == 0 && candidate.y <= 0;
        };
    }

    private static void addRefractureTranslationCandidates(
            Game game, List<Point> candidates, Point refractureTile, Point mapTile, int gap) {
        switch (game.getStoredValue("refractureLayoutEdge")) {
            case "UPPER_RIGHT", "LOWER_RIGHT" ->
                candidates.add(new Point(mapTile.x + TILE_WIDTH + gap - refractureTile.x, 0));
            case "BOTTOM" -> candidates.add(new Point(0, mapTile.y + SPACE_FOR_TILE_HEIGHT + gap - refractureTile.y));
            case "LOWER_LEFT", "UPPER_LEFT" ->
                candidates.add(new Point(mapTile.x - gap - TILE_WIDTH - refractureTile.x, 0));
            default -> candidates.add(new Point(0, mapTile.y - gap - SPACE_FOR_TILE_HEIGHT - refractureTile.y));
        }
    }

    private static boolean tilesOverlap(List<Point> first, List<Point> second) {
        return tilesOverlap(first, second, new Point());
    }

    private static boolean tilesOverlap(List<Point> first, List<Point> second, Point translation) {
        return first.stream()
                .anyMatch(firstPoint -> second.stream()
                        .anyMatch(secondPoint -> tileBoundsOverlap(
                                new Point(firstPoint.x + translation.x, firstPoint.y + translation.y), secondPoint)));
    }

    private static boolean tileBoundsOverlap(Point first, Point second) {
        return first.x < second.x + TILE_WIDTH
                && first.x + TILE_WIDTH > second.x
                && first.y < second.y + SPACE_FOR_TILE_HEIGHT
                && first.y + SPACE_FOR_TILE_HEIGHT > second.y;
    }

    private static Point scaleTilePosition(
            Game game, String position, int x, int y, int fractureYbump, boolean orientRefracture) {
        boolean rotatedRefracture = orientRefracture && isRotatedRefractureTile(game, position);
        if (orientRefracture) {
            Point refracturePosition = orientRefractureTile(game, position, x, y);
            x = refracturePosition.x;
            y = refracturePosition.y;
        }
        int layoutRings = layoutRingCount(game);
        int ringCount = Math.clamp(layoutRings, RING_MIN_COUNT, RING_MAX_COUNT);
        Optional<BoardPosition> board = BoardPosition.parse(position);
        if (board.isPresent()) {
            x -= extraBoardCompression(board.get().index(), boardLayoutRingCount(layoutRings));
        }
        if (ringCount == RING_MIN_COUNT) {
            x += HORIZONTAL_TILE_SPACING;
        }
        if (ringCount < RING_MAX_COUNT) {
            int lower = RING_MAX_COUNT - ringCount;

            if ("tl".equalsIgnoreCase(position)) {
                y -= 150;
            } else if ("bl".equalsIgnoreCase(position)) {
                y -= lower * SPACE_FOR_TILE_HEIGHT * 2 - 150;
                y += fractureYbump;
            } else if ("tr".equalsIgnoreCase(position)) {
                x -= lower * HORIZONTAL_TILE_SPACING * 2;
                y -= 150;
            } else if ("br".equalsIgnoreCase(position)) {
                x -= lower * HORIZONTAL_TILE_SPACING * 2;
                y -= lower * SPACE_FOR_TILE_HEIGHT * 2 - 150;
                y += fractureYbump;
            } else if (position.startsWith("frac") && !rotatedRefracture) {
                x -= lower * HORIZONTAL_TILE_SPACING;
                if (fractureYbump == 400) {
                    y -= (400 - 300) / 2; // always 50
                } else {
                    y += 250;
                }
            } else {
                x -= lower * HORIZONTAL_TILE_SPACING;
                y -= lower * SPACE_FOR_TILE_HEIGHT;
                y += fractureYbump;
            }
            return new Point(x, y);
        }
        return new Point(x, y);
    }

    private static boolean isRefractureTile(Game game, String position) {
        return game.isRefractureMode() && position != null && position.startsWith("frac");
    }

    private static boolean isTopRefractureTile(Game game, String position) {
        return isRefractureTile(game, position) && "TOP".equals(game.getStoredValue("refractureLayoutEdge"));
    }

    private static boolean isLowerRefractureTile(Game game, String position) {
        return isRefractureTile(game, position)
                && switch (game.getStoredValue("refractureLayoutEdge")) {
                    case "LOWER_RIGHT", "LOWER_LEFT" -> true;
                    default -> false;
                };
    }

    private static Point orientRefractureTile(Game game, String position, int x, int y) {
        if (!game.isRefractureMode() || position == null || !position.startsWith("frac")) return new Point(x, y);
        int rotations = refractureLayoutRotations(game);
        int q = Math.floorDiv(x - 2080, HORIZONTAL_TILE_SPACING);
        int r = Math.floorDiv(y + 1350 - 2550 - 150 * q, SPACE_FOR_TILE_HEIGHT);
        for (int index = 0; index < rotations; index++) {
            int previousQ = q;
            q = -r;
            r += previousQ;
        }
        return new Point(2080 + HORIZONTAL_TILE_SPACING * q, 2550 + 150 * q + SPACE_FOR_TILE_HEIGHT * r);
    }

    private static boolean isRotatedRefractureTile(Game game, String position) {
        return game.isRefractureMode()
                && position != null
                && position.startsWith("frac")
                && refractureLayoutRotations(game) != 0;
    }

    private static int refractureLayoutRotations(Game game) {
        return switch (game.getStoredValue("refractureLayoutEdge")) {
            case "UPPER_RIGHT" -> 1;
            case "LOWER_RIGHT" -> 2;
            case "BOTTOM" -> 3;
            case "LOWER_LEFT" -> 4;
            case "UPPER_LEFT" -> 5;
            default -> 0;
        };
    }

    private record PositionedPoint(String position, Point point) {}

    @Nullable
    public static Point getTilePosition(String position) {
        Optional<BoardPosition> board = BoardPosition.parse(position);
        if (board.isEmpty()) {
            return getPosition(position, tileImageCoordinates);
        }
        Point local = getPosition(board.get().local(), tileImageCoordinates);
        if (local != null) {
            local.translate(extraBoardOffsetX(board.get().index()), 0);
        }
        return local;
    }

    private static Point getPosition(String position, Properties positionTileMap) {
        if (position == null) return null;
        String value = positionTileMap.getProperty(position);
        return getPoint(value);
    }

    private static Point getPoint(String value) {
        if (value != null) {
            StringTokenizer tokenizer = new StringTokenizer(value, ",");
            try {
                int x = Integer.parseInt(tokenizer.nextToken());
                int y = Integer.parseInt(tokenizer.nextToken());
                return new Point(x, y);
            } catch (Exception e) {
                BotLogger.error("Could not parse position coordinates", e);
            }
        }
        return null;
    }

    public static Point getPlayerStats(String id) {
        Point point = new Point();
        String info = stats.getProperty(id);
        if (info == null) {
            return new Point(0, 0);
        }

        try {
            StringTokenizer individualPoints = new StringTokenizer(info, ",");
            String x = individualPoints.nextToken();
            String y = individualPoints.nextToken();
            point = new Point(Integer.parseInt(x), Integer.parseInt(y));
        } catch (Exception e) {
            BotLogger.error("Could not parse player positions", e);
        }
        return point;
    }

    public static List<Point> getPlayerPosition(int playerPosition) {
        List<Point> positions = new ArrayList<>();
        String info = playerInfo8ring.getProperty(Integer.toString(playerPosition));
        if (info == null) {
            return positions;
        }

        try {
            StringTokenizer pointInfo = new StringTokenizer(info, ";");
            while (pointInfo.hasMoreTokens()) {
                StringTokenizer individualPoints = new StringTokenizer(pointInfo.nextToken(), ",");
                String x = individualPoints.nextToken();
                String y = individualPoints.nextToken();
                positions.add(new Point(Integer.parseInt(x), Integer.parseInt(y)));
            }
        } catch (Exception e) {
            BotLogger.error("Could not parse player positions", e);
        }

        return positions;
    }

    public static UnitTokenPosition getPlanetTokenPosition(String planetName) {
        if ("space".equals(planetName)) return null;

        PlanetModel planet = TileHelper.getPlanetById(planetName);
        if (planet.getUnitPositions() != null) {
            return SerializationUtils.clone(planet.getUnitPositions());
        }
        if (planet.getPlanetLayout() != null) {
            return planet.getPlanetLayout().generateUnitTokenPosition();
        }
        return null;
    }

    public static UnitTokenPosition getReinforcementsPosition(String unitId) {
        Object value = reinforcements.get(unitId);
        if (value == null) {
            return null;
        }
        UnitTokenPosition unitTokenPosition = new UnitTokenPosition(unitId);
        String valuePosition = (String) value;
        StringTokenizer tokenizer = new StringTokenizer(valuePosition, ";");
        while (tokenizer.hasMoreTokens()) {
            String nextPoint = tokenizer.nextToken();
            StringTokenizer positionTokenizer = new StringTokenizer(nextPoint, ",");
            if (positionTokenizer.countTokens() == 2) {
                int x = Integer.parseInt(positionTokenizer.nextToken());
                int y = Integer.parseInt(positionTokenizer.nextToken());
                Point point = new Point(x, y);
                unitTokenPosition.addPosition(unitId, point);
            }
        }
        return unitTokenPosition;
    }

    public static Point getUnitOffset() {
        return ShipPositionModel.getOffset();
    }

    public static Point getAllianceUnitOffset() {
        return ShipPositionModel.getAllianceOffset();
    }

    private static String getTileSpaceUnitLayout(String tileId) {
        return Optional.ofNullable(TileHelper.getTileById(tileId).getShipPositionsType())
                .orElse(ShipPositionModel.ShipPosition.TYPE08)
                .getPositions();
    }

    public static UnitTokenPosition getSpaceUnitPosition(String planetName, String tileID) {
        String shipPositionString = getTileSpaceUnitLayout(tileID);

        UnitTokenPosition unitTokenPosition = new UnitTokenPosition(planetName, false);
        StringTokenizer tokenizer = new StringTokenizer(shipPositionString, ";");
        while (tokenizer.hasMoreTokens()) {
            String positionTemp = tokenizer.nextToken();
            String position = positionTemp.stripLeading();
            if (!position.isEmpty()) {
                StringTokenizer tokenPositionTokenizer = new StringTokenizer(position, " ");
                if (tokenPositionTokenizer.countTokens() == 2) {
                    String id = tokenPositionTokenizer.nextToken();
                    String pointValue = tokenPositionTokenizer.nextToken();
                    StringTokenizer positionTokenizer = new StringTokenizer(pointValue, ",");
                    if (positionTokenizer.countTokens() == 2) {
                        int x = Integer.parseInt(positionTokenizer.nextToken());
                        int y = Integer.parseInt(positionTokenizer.nextToken());
                        Point point = new Point(x, y);
                        unitTokenPosition.addPosition(id, point);
                    }
                }
            }
        }
        return unitTokenPosition;
    }

    public static List<String> getPositionsInRing(String ringNumOrCorners, @Nullable Game game) {
        List<String> positions = new ArrayList<>();
        if ("corners".equals(ringNumOrCorners)) {
            positions.addAll(List.of("tl", "tr", "bl", "br"));
        } else {
            try {
                int ring = Integer.parseInt(ringNumOrCorners);
                int totalTiles = Math.max(6 * ring, 1);
                for (int x = 1; x <= totalTiles; x++) {
                    String pos = makeTileStr(ring, x);
                    if (isTilePositionValid(pos)) positions.add(pos);
                }
            } catch (Exception e) {
                return positions;
            }
        }
        if (game != null) positions.removeIf(pos -> game.getTileByPosition(pos) == null);
        return positions;
    }

    /**
     * @return List of tiles adjacent to position in clockwise compass order: [N, NE, SE, S, SW, NW]
     */
    public static List<String> getAdjacentTilePositions(String position) {
        Optional<BoardPosition> board = BoardPosition.parse(position);
        if (board.isPresent()) {
            return getAdjacentTilePositions(board.get().local()).stream()
                    .map(board.get()::withLocal)
                    .map(neighbour -> isTilePositionValid(neighbour) ? neighbour : "x")
                    .toList();
        }
        String property = tileAdjacencies.getProperty(position);
        if (property == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(property.split(",")).toList();
    }

    // Below is JAZZ's attempt to rewrite the rewrite mentioned below. Adjacencies should follow the correct order
    // (verification needed)
    // Below is an attempt to rewrite the above method to allow for ring count up to 16 without needing to specify
    // adjacencies within .prop file - currently stuck on ensuring the adjacencies follow the correct order of [N, NE,
    // SE, S, SW, NW]
    public static List<String> getAdjacentTilePositionsNew(String position) {
        List<String> adjacentTiles = new ArrayList<>();
        if (!Helper.isInteger(position)) return adjacentTiles;
        if ("000".equals(position)) return List.of("101", "102", "103", "104", "105", "106");

        int ring = Integer.parseInt(position) / 100;
        int tile = Integer.parseInt(position) % 100;
        int side = (tile - 1) / ring; // math
        boolean isCorner = ((tile - 1) % ring) == 0;

        // Define the different relevant spots
        String nextRing1 = makeTileStr(ring + 1, tile + side);
        String nextRing2 = makeTileStr(ring + 1, tile + side + 1);
        String sameRingNext = makeTileStr(ring, tile + 1);
        String prevRing1 = makeTileStr(ring - 1, tile - side);
        String prevRing2 = makeTileStr(ring - 1, tile - side - 1); // this tile is not used for "corner tiles"
        String sameRingPrev = makeTileStr(ring, tile - 1);
        String nextRing3 = makeTileStr(ring + 1, tile + side - 1); // and THIS tile is ONLY used for "corner tiles"

        // First 4 tiles are the same for corner/non-corner
        List<String> ordering = new ArrayList<>(List.of(nextRing1, nextRing2, sameRingNext, prevRing1));
        if (!isCorner) ordering.addAll(List.of(prevRing2, sameRingPrev));
        if (isCorner) ordering.addAll(List.of(sameRingPrev, nextRing3));

        // This ordering is essentially "go out from mecatol, then go clockwise"
        // So we need to rotate it backwards based on that initial direction
        Collections.rotate(ordering, -1 * side);
        return new ArrayList<>(ordering.stream()
                .map(pos -> isTilePositionValid(pos) ? pos : "x")
                .toList());
    }

    // tileNum will be modulused to within the bounds of the ring (e.g. "tile 7" in ring 1 will become "tile 1")
    private static String makeTileStr(int ring, int tileNum) {
        tileNum = ((tileNum - 1) % (ring * 6)) + 1;
        ring = Math.abs(ring);
        String ringStr = Integer.toString(ring);
        String tileStr = "00" + (ring == 0 ? "00" : tileNum);
        return ringStr + tileStr.substring(tileStr.length() - 2);
    }

    public static Boolean isCornerOfHexRing(String tileID) {
        if (!Helper.isInteger(tileID)) throw new IllegalArgumentException("Invalid tile ID: " + tileID);
        int ring = Integer.parseInt(tileID) / 100;
        for (int corner = 1; corner <= 6; corner++) {
            if (tileID.equals(getTileIDAtCornerPositionOfRing(ring, corner))) return true;
        }
        return false;
    }

    private static Integer getPositionWithinHexSide(String tileID) {
        if (!Helper.isInteger(tileID)) return null;
        int ring = Integer.parseInt(tileID) / 100;
        int tileNumber = Integer.parseInt(tileID) % 100;
        for (int corner = 1; corner <= 6; corner++) {
            int upperBound = 1 + ring * (corner);
            int lowerBound = 1 + ring * (corner - 1);
            if (tileNumber >= lowerBound && tileNumber < upperBound) return tileNumber - lowerBound;
        }
        return null;
    }

    private static String getTileIDAtPositionInRingSide(Integer ring, Integer side, Integer position) {
        if (ring == null || side == null || position == null) return null;
        return ring + String.format("%02d", 1 + ring * (side - 1) + position);
    }

    @Nullable
    public static Integer getRingSideNumberOfTileID(String tileID) {
        if (!Helper.isInteger(tileID)) return null;
        int ringNumber = Integer.parseInt(tileID) / 100;
        int tileNumber = Integer.parseInt(tileID) % 100;
        for (int corner = 1; corner <= 6; corner++) {
            int upperBound = 1 + ringNumber * (corner);
            int lowerBound = 1 + ringNumber * (corner - 1);
            if (tileNumber >= lowerBound && tileNumber < upperBound) return corner;
        }
        return null;
    }

    public static String getTileIDAtCornerPositionOfRing(int ring, int cornerNumber) {
        return ring + String.format("%02d", 1 + ring * (cornerNumber - 1));
    }

    public static int getLeftMostTileOffsetInGame(Game game) {
        return game.getTileMap().keySet().stream()
                .mapToInt(pos -> {
                    if (!Helper.isInteger(pos)) return 2080;
                    Point p = getTilePosition(pos);
                    return (p != null ? p.x : 2080);
                })
                .min()
                .orElse(0);
    }

    public static int getRightMostTileOffsetInGame(Game game) {
        return game.getTileMap().keySet().stream()
                .mapToInt(pos -> {
                    if (!Helper.isInteger(pos)) return 2080;
                    Point p = getTilePosition(pos);
                    return (p != null ? p.x : 2080);
                })
                .max()
                .orElse(0);
    }

    public static int getTopMostTileOffsetInGame(Game game) {
        return game.getTileMap().keySet().stream()
                .mapToInt(pos -> {
                    if (!Helper.isInteger(pos)) return 2550;
                    Point p = getTilePosition(pos);
                    return (p != null ? p.y : 2550);
                })
                .min()
                .orElse(0);
    }

    public static int getBottomMostTileOffsetInGame(Game game) {
        return game.getTileMap().keySet().stream()
                .mapToInt(pos -> {
                    if (!Helper.isInteger(pos)) return 2550;
                    Point p = getTilePosition(pos);
                    return (p != null ? p.y : 2550);
                })
                .max()
                .orElse(0);
    }
}
