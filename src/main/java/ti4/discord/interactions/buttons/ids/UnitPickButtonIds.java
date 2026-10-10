package ti4.discord.interactions.buttons.ids;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;

public final class UnitPickButtonIds {

    public static final String ASSIGN_HITS = "assignHits";
    public static final String ASSIGN_DAMAGE = "assignDamage";
    public static final String REPAIR_DAMAGE = "repairDamage";
    public static final String TACTICAL_MOVE = "unitTacticalMove";
    public static final String TACTICAL_REMOVE = "unitTacticalRemove";
    public static final List<String> ACTIONS =
            List.of(ASSIGN_HITS, ASSIGN_DAMAGE, REPAIR_DAMAGE, TACTICAL_MOVE, TACTICAL_REMOVE);

    private static final String SEPARATOR = "_";
    private static final String REVERSE_SUFFIX = "reverse";
    private static final List<String> HIT_ASSIGNMENT_ACTIONS = List.of(ASSIGN_HITS, ASSIGN_DAMAGE);

    private UnitPickButtonIds() {}

    public enum BulkCommand {
        ALL("All", ASSIGN_HITS),
        ALL_SHIPS("AllShips", ASSIGN_HITS),
        MOVE_ALL("moveAll", TACTICAL_MOVE, TACTICAL_REMOVE),
        REVERSE_ALL("reverseAll", TACTICAL_MOVE, TACTICAL_REMOVE),
        REMOVE_ALL("removeAll", TACTICAL_MOVE, TACTICAL_REMOVE),
        REMOVE_ALL_SHIPS("removeAllShips", TACTICAL_MOVE, TACTICAL_REMOVE);

        private final String key;
        private final Set<String> actions;

        BulkCommand(String key, String... actions) {
            this.key = key;
            this.actions = Set.of(actions);
        }

        public boolean appliesTo(String action) {
            return actions.contains(action);
        }

        private static Optional<BulkCommand> forKey(String action, String key) {
            return Arrays.stream(values())
                    .filter(command -> command.key.equals(key) && command.appliesTo(action))
                    .findFirst();
        }
    }

    public static String format(
            String action,
            String position,
            int amount,
            UnitKey unitKey,
            UnitState state,
            String planetName,
            boolean reverse) {
        List<String> segments = new ArrayList<>(List.of(action, position, Integer.toString(amount), unitKey.asyncID()));
        if (state != null && state != UnitState.none) segments.add(state.name());
        if (planetName != null) segments.add(planetName);
        segments.add(unitKey.getColor());
        if (reverse) segments.add(REVERSE_SUFFIX);
        return String.join(SEPARATOR, segments);
    }

    public static String formatBulk(String action, String position, BulkCommand command) {
        if (!command.appliesTo(action)) {
            throw new IllegalArgumentException(command.key + " is not a bulk command of " + action);
        }
        return action + SEPARATOR + position + SEPARATOR + command.key;
    }

    public static Parsed parse(String action, String buttonId) {
        return tryParse(action, buttonId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + action + " unit button id: " + buttonId));
    }

    public static Optional<Parsed> tryParse(String action, String buttonId) {
        String payload = payloadAfter(action, buttonId);
        if (payload == null) return Optional.empty();

        Deque<String> segments = new ArrayDeque<>(Arrays.asList(payload.split(SEPARATOR, -1)));
        if (segments.size() < 3 || segments.contains("")) return Optional.empty();

        String position = segments.removeFirst();
        Integer amount = parseAmount(segments.removeFirst());
        UnitType unitType = parseUnitType(segments.removeFirst());
        if (amount == null || unitType == null) return Optional.empty();

        UnitState state = takeState(segments);
        boolean reverse = REVERSE_SUFFIX.equals(segments.peekLast());
        if (reverse) segments.removeLast();

        String color = takeColor(segments);
        String planetName = segments.isEmpty() ? null : String.join(SEPARATOR, segments);
        return Optional.of(new Parsed(action, position, amount, unitType, state, planetName, color, reverse));
    }

    private static String takeColor(Deque<String> planetAndColor) {
        boolean colorPresent =
                planetAndColor.size() > 1 || (planetAndColor.size() == 1 && Mapper.isValidColor(planetAndColor.peek()));
        return colorPresent ? planetAndColor.removeLast() : null;
    }

    public static ParsedBulk parseBulk(String action, String buttonId) {
        return tryParseBulk(action, buttonId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + action + " bulk button id: " + buttonId));
    }

    public static Optional<ParsedBulk> tryParseBulk(String action, String buttonId) {
        String payload = payloadAfter(action, buttonId);
        if (payload == null) return Optional.empty();

        String[] segments = payload.split(SEPARATOR, -1);
        if (segments.length != 2 || segments[0].isEmpty()) return Optional.empty();
        return BulkCommand.forKey(action, segments[1]).map(command -> new ParsedBulk(action, segments[0], command));
    }

    public static Optional<String> hitAssignmentPosition(String buttonId) {
        return HIT_ASSIGNMENT_ACTIONS.stream()
                .map(action -> payloadAfter(action, buttonId))
                .filter(StringUtils::isNotEmpty)
                .map(payload -> StringUtils.substringBefore(payload, SEPARATOR))
                .findFirst();
    }

    private static String payloadAfter(String action, String buttonId) {
        String prefix = action + SEPARATOR;
        if (buttonId == null || !buttonId.startsWith(prefix)) return null;
        return buttonId.substring(prefix.length());
    }

    private static Integer parseAmount(String segment) {
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UnitType parseUnitType(String segment) {
        return Arrays.stream(UnitType.values())
                .filter(type -> type.getValue().equals(segment))
                .findFirst()
                .orElseGet(() -> Units.findUnitType(segment));
    }

    private static UnitState takeState(Deque<String> segments) {
        List<String> remaining = new ArrayList<>(segments);
        if (remaining.size() >= 2) {
            UnitState twoSegmentState = stateNamed(remaining.get(0) + SEPARATOR + remaining.get(1));
            if (twoSegmentState != null) {
                segments.removeFirst();
                segments.removeFirst();
                return twoSegmentState;
            }
        }
        UnitState state = remaining.isEmpty() ? null : stateNamed(remaining.getFirst());
        if (state == null) return UnitState.none;
        segments.removeFirst();
        return state;
    }

    private static UnitState stateNamed(String name) {
        return Arrays.stream(UnitState.values())
                .filter(state -> state.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    public record Parsed(
            String action,
            String position,
            int amount,
            UnitType unitType,
            UnitState state,
            String planetName,
            String color,
            boolean reverse) {

        public boolean onPlanet() {
            return planetName != null;
        }

        public boolean hasState() {
            return state != UnitState.none;
        }
    }

    public record ParsedBulk(String action, String position, BulkCommand command) {}
}
