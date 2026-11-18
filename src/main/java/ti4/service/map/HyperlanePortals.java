package ti4.service.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.StringHelper;
import ti4.service.map.PortalSlot.Flow;

@UtilityClass
public class HyperlanePortals {

    static final String KEY = "hyperlanePortals";
    private static final int PARSED_STORES_KEPT = 64;
    private static final Pattern TYPED_SLOT = Pattern.compile("(?:([0-6])=)?([A-Za-z])([<>]?)");
    private static final Map<String, Map<String, List<PortalSlot>>> PARSED =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Map<String, List<PortalSlot>>> eldest) {
                    return size() > PARSED_STORES_KEPT;
                }
            });

    public static boolean hasAny(Game game) {
        return !stored(game).isEmpty();
    }

    public static Map<String, List<PortalSlot>> all(Game game) {
        Map<String, List<PortalSlot>> portals = new TreeMap<>();
        stored(game).forEach((position, slots) -> {
            if (canHoldPortals(game, position)) portals.put(position, slots);
        });
        return portals;
    }

    public static List<PortalSlot> slotsOf(Game game, String position) {
        Map<String, List<PortalSlot>> portals = stored(game);
        if (portals.isEmpty()) {
            return List.of();
        }
        List<PortalSlot> slots = portals.get(position);
        return slots != null && canHoldPortals(game, position) ? slots : List.of();
    }

    public static List<String> positionsWithLetter(Game game, char letter) {
        List<String> positions = new ArrayList<>();
        stored(game).forEach((position, slots) -> {
            if (slots.stream().anyMatch(slot -> slot.letter() == letter) && canHoldPortals(game, position)) {
                positions.add(position);
            }
        });
        Collections.sort(positions);
        return positions;
    }

    public static Optional<String> trySet(Game game, String position, String typed) {
        Optional<String> problem = problemWith(game, position, typed);
        if (problem.isEmpty()) set(game, position, parseTyped(typed).slots());
        return problem;
    }

    public static Optional<String> problemWith(Game game, String position, String typed) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !CustomHyperlaneService.isCustomHyperlaneTile(tile)) {
            return Optional.of(position + " is not a custom hyperlane; portals go on roundabout hyperlanes.");
        }
        if (!canHoldPortals(game, position)) {
            return Optional.of(position + " isn't a roundabout. Portals sit on a roundabout's ring (or on an empty"
                    + " hyperlane, which becomes a portal interchange).");
        }
        return parseTyped(typed).problem();
    }

    public record Typed(List<PortalSlot> slots, Optional<String> problem) {}

    public static Typed parseTyped(String typed) {
        List<TypedEntry> entries = new ArrayList<>();
        Set<Integer> fixedSlots = new HashSet<>();
        Set<Character> usedLetters = new HashSet<>();
        for (String part : typed.split("[,;\\s]+")) {
            if (part.isBlank()) continue;
            Matcher matcher = TYPED_SLOT.matcher(part.trim());
            if (!matcher.matches()) {
                return problem("`" + part.trim() + "` isn't a portal. Write letters, optionally with a slot, for"
                        + " example `A, 3=B>, C<` (`>` entry-only, `<` exit-only).");
            }
            Integer slot = matcher.group(1) == null ? null : Integer.parseInt(matcher.group(1));
            char letter = Character.toUpperCase(matcher.group(2).charAt(0));
            if (slot != null && !fixedSlots.add(slot)) {
                return problem("Slot " + slot + " is listed twice.");
            }
            if (!usedLetters.add(letter)) {
                return problem("Letter " + letter + " is used twice on this hyperlane.");
            }
            entries.add(new TypedEntry(slot, letter, flowOf(matcher.group(3))));
        }
        List<PortalSlot> slots = new ArrayList<>();
        Set<Integer> taken = new HashSet<>(fixedSlots);
        for (TypedEntry entry : entries) {
            Integer slot = entry.slot() != null ? entry.slot() : firstFreeSlot(taken);
            if (slot == null) {
                return problem("A hyperlane holds at most " + PortalSlot.SLOTS + " portals.");
            }
            taken.add(slot);
            slots.add(new PortalSlot(slot, entry.letter(), entry.flow()));
        }
        if (!slots.isEmpty() && slots.stream().noneMatch(slot -> slot.slot() == PortalSlot.CENTRE)) {
            PortalSlot first = slots.getFirst();
            slots.set(0, new PortalSlot(PortalSlot.CENTRE, first.letter(), first.flow()));
        }
        slots.sort(Comparator.comparingInt(PortalSlot::slot));
        return new Typed(List.copyOf(slots), Optional.empty());
    }

    private record TypedEntry(@Nullable Integer slot, char letter, Flow flow) {}

    private static Typed problem(String message) {
        return new Typed(List.of(), Optional.of(message));
    }

    private static Flow flowOf(String marker) {
        if (">".equals(marker)) return Flow.ENTRY_ONLY;
        if ("<".equals(marker)) return Flow.EXIT_ONLY;
        return Flow.BOTH;
    }

    @Nullable
    private static Integer firstFreeSlot(Set<Integer> taken) {
        for (int slot = 0; slot < PortalSlot.SLOTS; slot++) {
            if (!taken.contains(slot)) return slot;
        }
        return null;
    }

    public static String encodeCompact(List<PortalSlot> slots) {
        return slots.stream()
                .map(slot -> "" + slot.slot() + slot.letter() + slot.flow().marker())
                .collect(Collectors.joining());
    }

    public static String typedFromCompact(String compact) {
        return compact.replaceAll("([0-6])([A-Za-z])([<>]?)", "$1=$2$3,").replaceAll(",$", "");
    }

    public static String describe(List<PortalSlot> slots) {
        return slots.stream().map(PortalSlot::typed).collect(Collectors.joining(", "));
    }

    public static void set(Game game, String position, List<PortalSlot> slots) {
        Map<String, List<PortalSlot>> portals = new TreeMap<>(stored(game));
        if (slots.isEmpty()) {
            portals.remove(position);
        } else {
            portals.put(position, List.copyOf(slots));
        }
        write(game, portals);
    }

    public static void remove(Game game, String position) {
        Map<String, List<PortalSlot>> portals = new TreeMap<>(stored(game));
        if (portals.remove(position) != null) write(game, portals);
    }

    public static void clear(Game game) {
        game.removeStoredValue(KEY);
    }

    public static void moveAll(Game game, Map<String, String> fromTo) {
        Map<String, List<PortalSlot>> portals = stored(game);
        if (portals.isEmpty()) {
            return;
        }
        Map<String, List<PortalSlot>> moved = new TreeMap<>();
        portals.forEach((position, slots) -> {
            if (!fromTo.containsKey(position)) moved.put(position, slots);
        });
        portals.forEach((position, slots) -> {
            if (fromTo.containsKey(position)) moved.put(fromTo.get(position), slots);
        });
        write(game, moved);
    }

    public static void rotate(Game game, String position, int steps) {
        List<PortalSlot> slots = stored(game).get(position);
        if (slots == null) {
            return;
        }
        List<PortalSlot> turned = new ArrayList<>();
        for (PortalSlot slot : slots) turned.add(slot.rotatedBy(steps));
        turned.sort(Comparator.comparingInt(PortalSlot::slot));
        set(game, position, turned);
    }

    public static boolean canHoldPortals(Game game, String position) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !CustomHyperlaneService.isCustomHyperlaneTile(tile)) {
            return false;
        }
        String matrix = game.getCustomHyperlaneData().get(position);
        return matrix == null || !matrix.contains("1") || CustomHyperlaneService.hasSelfConnection(matrix);
    }

    private static Map<String, List<PortalSlot>> stored(Game game) {
        String raw = game.getStoredValueMap().get(KEY);
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, List<PortalSlot>> parsed = PARSED.get(raw);
        if (parsed == null) {
            parsed = parse(StringHelper.unescape(raw));
            PARSED.put(raw, parsed);
        }
        return parsed;
    }

    private static void write(Game game, Map<String, List<PortalSlot>> portals) {
        if (portals.isEmpty()) {
            game.removeStoredValue(KEY);
            return;
        }
        StringBuilder encoded = new StringBuilder();
        new TreeMap<>(portals).forEach((position, slots) -> {
            if (!encoded.isEmpty()) encoded.append('|');
            encoded.append(position).append('=');
            encoded.append(slots.stream()
                    .map(slot -> slot.slot() + ":" + slot.letter() + ":"
                            + slot.flow().name())
                    .collect(Collectors.joining(",")));
        });
        game.setStoredValue(KEY, encoded.toString());
    }

    static Map<String, List<PortalSlot>> parse(String encoded) {
        Map<String, List<PortalSlot>> portals = new LinkedHashMap<>();
        for (String entry : encoded.split("\\|")) {
            int split = entry.indexOf('=');
            if (split <= 0) continue;
            List<PortalSlot> slots = new ArrayList<>();
            for (String part : entry.substring(split + 1).split(",")) {
                PortalSlot slot = parseStoredSlot(part);
                if (slot != null) slots.add(slot);
            }
            if (!slots.isEmpty()) portals.put(entry.substring(0, split), List.copyOf(slots));
        }
        return Map.copyOf(portals);
    }

    @Nullable
    private static PortalSlot parseStoredSlot(String part) {
        String[] fields = part.split(":");
        if (fields.length != 3 || fields[1].length() != 1) return null;
        try {
            int slot = Integer.parseInt(fields[0]);
            if (slot < 0 || slot >= PortalSlot.SLOTS) return null;
            return new PortalSlot(slot, fields[1].charAt(0), Flow.valueOf(fields[2]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
