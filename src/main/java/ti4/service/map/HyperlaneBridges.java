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
    private static final Map<String, Map<String, BridgeMatrix>> PARSED = new ConcurrentHashMap<>();

    public static Map<String, BridgeMatrix> all(Game game) {
        String raw = game.getStoredValueMap().get(KEY);
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, BridgeMatrix> parsed = PARSED.get(raw);
        if (parsed == null) {
            if (PARSED.size() >= PARSED_STORES_KEPT) PARSED.clear();
            parsed = parse(StringHelper.unescape(raw));
            PARSED.put(raw, parsed);
        }
        return parsed;
    }

    @Nullable
    public static BridgeMatrix matrixOf(Game game, String position) {
        Map<String, BridgeMatrix> bridges = all(game);
        if (bridges.isEmpty()) {
            return null;
        }
        BridgeMatrix matrix = bridges.get(position);
        if (matrix == null) {
            return null;
        }
        Tile tile = game.getTileByPosition(position);
        return tile == null || isHyperlane(tile) ? null : matrix;
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
        Map<String, BridgeMatrix> bridges = new TreeMap<>(all(game));
        if (matrix == null || matrix.isEmpty()) {
            bridges.remove(position);
        } else {
            bridges.put(position, matrix);
        }
        write(game, bridges);
    }

    public static void remove(Game game, String position) {
        Map<String, BridgeMatrix> bridges = new TreeMap<>(all(game));
        if (bridges.remove(position) != null) write(game, bridges);
    }

    public static void clear(Game game) {
        game.removeStoredValue(KEY);
    }

    public static void moveAll(Game game, Map<String, String> fromTo) {
        Map<String, BridgeMatrix> bridges = all(game);
        if (bridges.isEmpty()) {
            return;
        }
        Map<String, BridgeMatrix> moved = new TreeMap<>();
        bridges.forEach((position, matrix) -> moved.put(fromTo.getOrDefault(position, position), matrix));
        write(game, moved);
    }

    public static boolean isHyperlane(Tile tile) {
        return CustomHyperlaneService.isCustomHyperlaneTile(tile) || Mapper.getHyperlaneData(tile.getTileID()) != null;
    }

    public static String encode(Map<String, BridgeMatrix> bridges) {
        StringBuilder encoded = new StringBuilder();
        new TreeMap<>(bridges).forEach((position, matrix) -> {
            if (!encoded.isEmpty()) encoded.append('|');
            encoded.append(position).append('=').append(matrix.stored());
        });
        return encoded.toString();
    }

    private static void write(Game game, Map<String, BridgeMatrix> bridges) {
        if (bridges.isEmpty()) {
            game.removeStoredValue(KEY);
        } else {
            game.setStoredValue(KEY, encode(bridges));
        }
    }

    static Map<String, BridgeMatrix> parse(String encoded) {
        Map<String, BridgeMatrix> bridges = new LinkedHashMap<>();
        for (String entry : encoded.split("\\|")) {
            int split = entry.indexOf('=');
            if (split <= 0) continue;
            BridgeMatrix matrix = BridgeMatrix.parse(entry.substring(split + 1));
            if (matrix != null && !matrix.isEmpty()) bridges.put(entry.substring(0, split), matrix);
        }
        return Map.copyOf(bridges);
    }
}
