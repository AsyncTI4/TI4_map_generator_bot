package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.StrategyCard;

public record CardPlan(StrategyCard card, int initiative, List<Benefit> benefits) {

    private static final String PART = ";";
    private static final String FIELD = ":";

    public record Benefit(CardUse use, double value, String target) {

        public Benefit(CardUse use, double value) {
            this(use, value, "");
        }

        public boolean hasTarget() {
            return !target.isEmpty();
        }
    }

    public CardPlan {
        benefits = List.copyOf(benefits);
    }

    public double value() {
        return benefits.stream().mapToDouble(Benefit::value).sum();
    }

    public Optional<Benefit> main() {
        return benefits.stream().max(Comparator.comparingDouble(Benefit::value));
    }

    public Optional<Benefit> benefit(CardUse use) {
        return benefits.stream().filter(benefit -> benefit.use() == use).findFirst();
    }

    public boolean has(CardUse use) {
        return benefit(use).isPresent();
    }

    public String describe() {
        List<String> parts = benefits.stream()
                .filter(benefit -> benefit.value() != 0)
                .sorted(Comparator.comparingDouble(Benefit::value).reversed())
                .map(benefit -> benefit.use().label() + " (" + signed(benefit.value()) + ")")
                .toList();
        String name = StringUtils.capitalize(card.name().toLowerCase(Locale.ROOT));
        return parts.isEmpty() ? name + ": no use" : name + ": " + String.join(", ", parts);
    }

    public String encode() {
        return String.join(
                PART,
                benefits.stream()
                        .map(benefit -> String.join(
                                FIELD,
                                benefit.use().name(),
                                String.format(Locale.ROOT, "%.2f", benefit.value()),
                                benefit.target()))
                        .toList());
    }

    public static Optional<CardPlan> decode(StrategyCard card, int initiative, String encoded) {
        List<Benefit> benefits = new ArrayList<>();
        try {
            for (String part : StringUtils.split(encoded, PART)) {
                String[] fields = part.split(FIELD, -1);
                if (fields.length != 3) return Optional.empty();
                benefits.add(new Benefit(CardUse.valueOf(fields[0]), Double.parseDouble(fields[1]), fields[2]));
            }
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        return Optional.of(new CardPlan(card, initiative, benefits));
    }

    private static String signed(double value) {
        return String.format(Locale.ROOT, "%+.1f", value);
    }
}
