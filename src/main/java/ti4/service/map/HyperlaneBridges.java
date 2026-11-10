package ti4.service.map;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.StringHelper;
import ti4.model.Mapper;

@UtilityClass
public class HyperlaneBridges {

    static final String KEY = "hyperlaneBridges";
    private static final int PARSED_STORES_KEPT = 64;
    private static final Map<String, Map<String, Stored>> PARSED = new ConcurrentHashMap<>();

    record Stored(@Nullable String tileId, BridgeMatrix matrix) {

        boolean belongsTo(@Nullable Tile tile) {
            return tile != null && !isHyperlane(tile) && (tileId == null || tileId.equals(tile.getTileID()));
        }
    }

    public static boolean hasAny(Game game) {
        return !stored(game).isEmpty();
    }

    public static Map<String, BridgeMatrix> all(Game game) {
        Map<String, BridgeMatrix> bridges = new TreeMap<>();
        stored(game).forEach((position, stored) -> {
            if (stored.belongsTo(game.getTileByPosition(position))) bridges.put(position, stored.matrix());
        });
        return bridges;
    }

    @Nullable
    public static BridgeMatrix matrixOf(Game game, String position) {
        Map<String, Stored> bridges = stored(game);
        if (bridges.isEmpty()) {
            return null;
        }
        Stored stored = bridges.get(position);
        return stored != null && stored.belongsTo(game.getTileByPosition(position)) ? stored.matrix() : null;
    }

    public static Set<Integer> laneSides(Game game, String position) {
        BridgeMatrix matrix = matrixOf(game, position);
        return matrix == null ? Set.of() : matrix.sides();
    }

    @Nullable
    public static Set<Integer> exits(Game game, String position, int enteredSide, boolean bothWays) {
        BridgeMatrix matrix = matrixOf(game, position);
        if (matrix == null || !matrix.sides().contains(enteredSide)) {
            return null;
        }
        return matrix.exitsFrom(enteredSide, bothWays);
    }

    public static Set<Integer> entriesLeadingTo(Game game, String position, int exitSide, boolean bothWays) {
        BridgeMatrix matrix = matrixOf(game, position);
        if (matrix == null || !matrix.sides().contains(exitSide)) {
            return Set.of();
        }
        return matrix.entriesLeadingTo(exitSide, bothWays);
    }

    public static Optional<String> problemWith(Game game, String position, String typed) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            return Optional.of("There is no tile at " + position + ".");
        }
        if (isHyperlane(tile)) {
            return Optional.of(
                    position + " is a hyperlane; bridges go on systems. Edit hyperlanes with their own button.");
        }
        if (!CustomHyperlaneService.isValidConnectionMatrix(typed)) {
            return Optional.of("The matrix needs 6 rows of 6 values (0, 1 or 2), rows separated by `;`.");
        }
        int[][] grid = CustomHyperlaneService.parseMatrix(typed);
        for (int side = 0; side < 6; side++) {
            if (grid[side][side] != 0) {
                return Optional.of("A bridge can't connect a side to itself (no roundabouts); keep the diagonal at 0.");
            }
        }
        BridgeMatrix matrix = BridgeMatrix.parse(CustomHyperlaneService.toStoredMatrix(typed));
        return matrix == null ? Optional.empty() : matrix.layoutProblem();
    }

    public static void set(Game game, String position, String typed) {
        BridgeMatrix matrix = BridgeMatrix.parse(CustomHyperlaneService.toStoredMatrix(typed));
        Map<String, Stored> bridges = new TreeMap<>(stored(game));
        Tile tile = game.getTileByPosition(position);
        if (matrix == null || matrix.isEmpty() || tile == null) {
            bridges.remove(position);
        } else {
            bridges.put(position, new Stored(tile.getTileID(), matrix));
        }
        write(game, bridges);
    }

    public static void remove(Game game, String position) {
        Map<String, Stored> bridges = new TreeMap<>(stored(game));
        if (bridges.remove(position) != null) write(game, bridges);
    }

    public static void clear(Game game) {
        game.removeStoredValue(KEY);
    }

    public static void move(Game game, String from, String to, boolean swap) {
        if (from.equals(to)) {
            return;
        }
        moveAll(game, swap ? Map.of(from, to, to, from) : Map.of(from, to));
    }

    public static void moveAll(Game game, Map<String, String> fromTo) {
        Map<String, Stored> bridges = stored(game);
        if (bridges.isEmpty()) {
            return;
        }
        Map<String, Stored> moved = new TreeMap<>();
        bridges.forEach((position, stored) -> {
            if (!fromTo.containsKey(position)) moved.put(position, stored);
        });
        bridges.forEach((position, stored) -> {
            if (fromTo.containsKey(position)) moved.put(fromTo.get(position), stored);
        });
        write(game, moved);
    }

    public static boolean isHyperlane(Tile tile) {
        return CustomHyperlaneService.isCustomHyperlaneTile(tile) || Mapper.getHyperlaneData(tile.getTileID()) != null;
    }

    private static Map<String, Stored> stored(Game game) {
        String raw = game.getStoredValueMap().get(KEY);
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Stored> parsed = PARSED.get(raw);
        if (parsed == null) {
            if (PARSED.size() >= PARSED_STORES_KEPT) PARSED.clear();
            parsed = parse(StringHelper.unescape(raw));
            PARSED.put(raw, parsed);
        }
        return parsed;
    }

    private static void write(Game game, Map<String, Stored> bridges) {
        if (bridges.isEmpty()) {
            game.removeStoredValue(KEY);
            return;
        }
        StringBuilder encoded = new StringBuilder();
        new TreeMap<>(bridges).forEach((position, stored) -> {
            if (!encoded.isEmpty()) encoded.append('|');
            encoded.append(position).append('=');
            if (stored.tileId() != null) encoded.append(stored.tileId()).append('@');
            encoded.append(stored.matrix().stored());
        });
        game.setStoredValue(KEY, encoded.toString());
    }

    static Map<String, Stored> parse(String encoded) {
        Map<String, Stored> bridges = new LinkedHashMap<>();
        for (String entry : encoded.split("\\|")) {
            int split = entry.indexOf('=');
            if (split <= 0) continue;
            String value = entry.substring(split + 1);
            int at = value.indexOf('@');
            String tileId = at > 0 ? value.substring(0, at) : null;
            BridgeMatrix matrix = BridgeMatrix.parse(at > 0 ? value.substring(at + 1) : value);
            if (matrix != null && !matrix.isEmpty()) bridges.put(entry.substring(0, split), new Stored(tileId, matrix));
        }
        return Map.copyOf(bridges);
    }
}
