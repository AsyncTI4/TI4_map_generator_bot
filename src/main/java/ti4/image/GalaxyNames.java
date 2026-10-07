package ti4.image;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;

public final class GalaxyNames {

    public static final String MAIN_ID = "main";
    public static final List<String> IDS = List.of(MAIN_ID, "a", "b", "c", "d", "e", "f", "g");
    private static final String STORAGE_KEY = "fowGalaxyNames";

    // spotless:off
    static final List<String> NAMES = List.of(
            "milky-way", "andromeda", "triangulum", "whirlpool", "sombrero", "pinwheel", "cartwheel", "sunflower",
            "cigar", "black-eye", "tadpole", "sculptor", "fornax", "magellan", "centaurus", "bodes",
            "antennae", "mice", "hoags", "needle", "condor", "medusa", "phantom", "southern-pinwheel");
    // spotless:on

    private GalaxyNames() {}

    public static boolean isMultiGalaxy(Game game) {
        return !BoardPosition.boardsInUse(game).isEmpty();
    }

    public static List<String> inUse(Game game) {
        List<String> ids = new ArrayList<>(List.of(MAIN_ID));
        BoardPosition.boardsInUse(game).forEach(board -> ids.add(String.valueOf(board)));
        return ids;
    }

    public static String idOf(char board) {
        return board == BoardPosition.MAIN_BOARD ? MAIN_ID : String.valueOf(board);
    }

    public static String name(Game game, String id) {
        return names(game).getOrDefault(id, id);
    }

    public static boolean isManual(Game game, String id) {
        return manualNames(game).containsKey(id);
    }

    public static Map<String, String> names(Game game) {
        Map<String, String> manual = manualNames(game);
        Set<String> taken = new HashSet<>(manual.values());
        Map<String, String> names = new LinkedHashMap<>();
        for (String id : IDS) {
            String name = manual.get(id);
            if (name == null) {
                name = autoName(game, id, taken);
                taken.add(name);
            }
            names.put(id, name);
        }
        return names;
    }

    @Nullable
    public static String rename(Game game, String id, String name) {
        if (!IDS.contains(id)) {
            return "Galaxies are `main` and `a` to `g`.";
        }
        if (!MapSegment.isValidName(name) || MapSegment.isReservedName(name)) {
            return "Galaxy names use lowercase letters, digits and `-`, up to 20 characters, and cannot be `main`,"
                    + " `fracture` or `board-…`.";
        }
        Map<String, String> current = names(game);
        Map<String, String> manualNow = manualNames(game);
        List<String> inUse = inUse(game);
        boolean usedElsewhere = IDS.stream()
                .filter(other -> !other.equals(id))
                .anyMatch(other -> name.equals(manualNow.get(other))
                        || (inUse.contains(other) && name.equals(current.get(other))));
        if (usedElsewhere) {
            return "Another galaxy is already called `" + name + "`.";
        }
        Map<String, String> manual = manualNames(game);
        manual.put(id, name);
        save(game, manual);
        return null;
    }

    public static void resetToAuto(Game game, String id) {
        Map<String, String> manual = manualNames(game);
        manual.remove(id);
        save(game, manual);
    }

    private static String autoName(Game game, String id, Set<String> taken) {
        int start = Math.floorMod((game.getName() + ":" + id).hashCode(), NAMES.size());
        if (MAIN_ID.equals(id) && !taken.contains(NAMES.getFirst())) {
            return NAMES.getFirst();
        }
        for (int offset = 0; offset < NAMES.size(); offset++) {
            String candidate = NAMES.get((start + offset) % NAMES.size());
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return "galaxy-" + id;
    }

    private static Map<String, String> manualNames(Game game) {
        String stored = game.getStoredValue(STORAGE_KEY);
        Map<String, String> manual = new LinkedHashMap<>();
        if (StringUtils.isBlank(stored)) {
            return manual;
        }
        Arrays.stream(stored.split(";"))
                .map(entry -> entry.split("=", 2))
                .filter(parts -> parts.length == 2 && IDS.contains(parts[0]) && MapSegment.isValidName(parts[1]))
                .forEach(parts -> manual.put(parts[0], parts[1]));
        return manual;
    }

    private static void save(Game game, Map<String, String> manual) {
        if (manual.isEmpty()) {
            game.removeStoredValue(STORAGE_KEY);
            return;
        }
        game.setStoredValue(
                STORAGE_KEY,
                manual.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining(";")));
    }
}
