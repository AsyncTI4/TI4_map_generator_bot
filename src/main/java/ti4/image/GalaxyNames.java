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
    private static final String ASSIGNED_KEY = "fowGalaxyNames";
    private static final String RENAMED_KEY = "fowGalaxyRenames";

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
        return read(game, RENAMED_KEY).containsKey(id);
    }

    public static Map<String, String> names(Game game) {
        Map<String, String> assigned = read(game, ASSIGNED_KEY);
        Map<String, String> renamed = read(game, RENAMED_KEY);
        Set<String> taken = new HashSet<>(assigned.values());
        taken.addAll(renamed.values());
        Map<String, String> names = new LinkedHashMap<>();
        for (String id : IDS) {
            String name = renamed.getOrDefault(id, assigned.get(id));
            if (name == null) {
                name = autoName(game, id, taken);
                taken.add(name);
            }
            names.put(id, name);
        }
        return names;
    }

    public static void ensureAssigned(Game game) {
        if (!isMultiGalaxy(game)) {
            return;
        }
        Map<String, String> assigned = read(game, ASSIGNED_KEY);
        List<String> missing =
                inUse(game).stream().filter(id -> !assigned.containsKey(id)).toList();
        if (missing.isEmpty()) {
            return;
        }
        Set<String> taken = new HashSet<>(assigned.values());
        taken.addAll(read(game, RENAMED_KEY).values());
        for (String id : missing) {
            String name = autoName(game, id, taken);
            taken.add(name);
            assigned.put(id, name);
        }
        save(game, ASSIGNED_KEY, assigned);
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
        Map<String, String> assigned = read(game, ASSIGNED_KEY);
        Map<String, String> renamed = read(game, RENAMED_KEY);
        Map<String, String> current = names(game);
        List<String> inUse = inUse(game);
        boolean usedElsewhere = IDS.stream()
                .filter(other -> !other.equals(id))
                .filter(other -> inUse.contains(other) || assigned.containsKey(other) || renamed.containsKey(other))
                .anyMatch(other -> name.equals(current.get(other)) || name.equals(assigned.get(other)));
        if (usedElsewhere) {
            return "Another galaxy is already called `" + name + "`.";
        }
        renamed.put(id, name);
        save(game, RENAMED_KEY, renamed);
        return null;
    }

    public static void resetToAuto(Game game, String id) {
        Map<String, String> renamed = read(game, RENAMED_KEY);
        renamed.remove(id);
        save(game, RENAMED_KEY, renamed);
    }

    public static void clearNames(Game game) {
        game.removeStoredValue(ASSIGNED_KEY);
        game.removeStoredValue(RENAMED_KEY);
    }

    private static String autoName(Game game, String id, Set<String> taken) {
        if (MAIN_ID.equals(id) && !taken.contains(NAMES.getFirst())) {
            return NAMES.getFirst();
        }
        int start = Math.floorMod((game.getName() + ":" + id).hashCode(), NAMES.size());
        for (int offset = 0; offset < NAMES.size(); offset++) {
            String candidate = NAMES.get((start + offset) % NAMES.size());
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return "galaxy-" + id;
    }

    private static Map<String, String> read(Game game, String key) {
        String stored = game.getStoredValue(key);
        Map<String, String> names = new LinkedHashMap<>();
        if (StringUtils.isBlank(stored)) {
            return names;
        }
        Arrays.stream(stored.split(";"))
                .map(entry -> entry.split("=", 2))
                .filter(parts -> parts.length == 2 && IDS.contains(parts[0]) && MapSegment.isValidName(parts[1]))
                .forEach(parts -> names.put(parts[0], parts[1]));
        return names;
    }

    private static void save(Game game, String key, Map<String, String> names) {
        if (names.isEmpty()) {
            game.removeStoredValue(key);
            return;
        }
        game.setStoredValue(
                key,
                names.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining(";")));
    }
}
