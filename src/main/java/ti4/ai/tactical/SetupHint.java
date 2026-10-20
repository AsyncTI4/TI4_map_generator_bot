package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;

public record SetupHint(TacticalPlan.Kind kind, String position, double bonus) {

    private static final String KEY_PREFIX = "setupHint|";
    private static final String PART = ";";
    private static final String AT = "@";
    private static final String BONUS = ":";

    public static String key(Game game) {
        return KEY_PREFIX + game.getRound();
    }

    public String encode() {
        return kind.name() + AT + position + BONUS + String.format(Locale.ROOT, "%.2f", bonus);
    }

    public static String encodeAll(Collection<SetupHint> hints) {
        return String.join(PART, hints.stream().map(SetupHint::encode).toList());
    }

    public static List<SetupHint> decodeAll(String encoded) {
        List<SetupHint> hints = new ArrayList<>();
        for (String part : StringUtils.split(StringUtils.defaultString(encoded), PART)) {
            String kind = StringUtils.substringBefore(part, AT);
            String position = StringUtils.substringBetween(part, AT, BONUS);
            String bonus = StringUtils.substringAfterLast(part, BONUS);
            if (StringUtils.isEmpty(position)) return List.of();
            try {
                hints.add(new SetupHint(TacticalPlan.Kind.valueOf(kind), position, Double.parseDouble(bonus)));
            } catch (RuntimeException e) {
                return List.of();
            }
        }
        return hints;
    }

    boolean matches(TacticalPlan plan) {
        return plan.kind() == kind && plan.target().equals(position);
    }

    static double bonusFor(List<SetupHint> hints, TacticalPlan plan) {
        return hints.stream()
                .filter(hint -> hint.matches(plan))
                .mapToDouble(SetupHint::bonus)
                .sum();
    }
}
