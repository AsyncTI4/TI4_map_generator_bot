package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.Units.UnitType;

public record TacticalPlan(
        Kind kind, String target, List<UnitMove> moves, Map<String, Integer> landings, double score) {

    public enum Kind {
        EXPAND,
        ATTACK,
        PRODUCE,
        POSITION
    }

    public record UnitMove(String origin, String holder, UnitType type, int count) {}

    private static final String PART = "|";
    private static final String ITEM = ";";
    private static final String FIELD = "~";

    public TacticalPlan withScore(double newScore) {
        return new TacticalPlan(kind, target, moves, landings, newScore);
    }

    public String encode() {
        List<String> moveItems = new ArrayList<>();
        for (UnitMove move : moves) {
            moveItems.add(
                    String.join(FIELD, move.origin(), move.holder(), move.type().name(), String.valueOf(move.count())));
        }
        List<String> landingItems = new ArrayList<>();
        landings.forEach((planet, count) -> landingItems.add(planet + FIELD + count));
        return String.join(
                PART,
                kind.name(),
                target,
                String.join(ITEM, moveItems),
                String.join(ITEM, landingItems),
                String.valueOf(score));
    }

    public static Optional<TacticalPlan> decode(String encoded) {
        String[] parts = StringUtils.splitPreserveAllTokens(encoded, PART);
        if (parts == null || parts.length != 5) return Optional.empty();
        try {
            List<UnitMove> moves = new ArrayList<>();
            for (String item : StringUtils.split(parts[2], ITEM)) {
                String[] fields = item.split(FIELD);
                moves.add(new UnitMove(fields[0], fields[1], UnitType.valueOf(fields[2]), Integer.parseInt(fields[3])));
            }
            Map<String, Integer> landings = new LinkedHashMap<>();
            for (String item : StringUtils.split(parts[3], ITEM)) {
                String[] fields = item.split(FIELD);
                landings.put(fields[0], Integer.parseInt(fields[1]));
            }
            return Optional.of(
                    new TacticalPlan(Kind.valueOf(parts[0]), parts[1], moves, landings, Double.parseDouble(parts[4])));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
