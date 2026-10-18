package ti4.service.testbed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.image.Mapper;

@UtilityClass
public class TestBedPlaceholders {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)([:.])([a-zA-Z0-9_]+)}");
    public static final List<String> CARD_KINDS = List.of("ac", "so", "pn");
    public static final List<String> SEAT_ATTRIBUTES = List.of("faction", "color");
    private static final int MAX_NESTING = 3;

    public record Resolution(String text, List<String> problems) {}

    public static boolean hasPlaceholders(String text) {
        return PLACEHOLDER.matcher(text).find();
    }

    public static Resolution resolve(String text, @Nullable Player actor, Function<String, Player> seats) {
        String current = text;
        for (int depth = 0; depth < MAX_NESTING; depth++) {
            Resolution pass = resolveOnce(current, actor, seats);
            if (!pass.problems().isEmpty() || pass.text().equals(current)) return pass;
            current = pass.text();
        }
        return new Resolution(current, List.of());
    }

    private static Resolution resolveOnce(String text, @Nullable Player actor, Function<String, Player> seats) {
        List<String> problems = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String value = ":".equals(matcher.group(2))
                    ? handNumber(matcher.group(1), matcher.group(3), actor, problems)
                    : seatAttribute(matcher.group(1), matcher.group(3), seats, problems);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(resolved);
        return new Resolution(resolved.toString(), problems);
    }

    @Nullable
    private static String handNumber(String kind, String cardId, @Nullable Player actor, List<String> problems) {
        if (actor == null) {
            problems.add("`{" + kind + ":" + cardId + "}` needs a seat (`as`)");
            return null;
        }
        Map<String, Integer> hand = hand(kind, actor);
        if (hand == null) {
            problems.add("unknown card kind `" + kind + "`; use " + CARD_KINDS);
            return null;
        }
        Integer number = hand.get(cardId);
        if (number == null) {
            problems.add(actor.getFaction() + " has no `" + cardId + "` in hand; hand: " + hand.keySet());
            return null;
        }
        return String.valueOf(number);
    }

    @Nullable
    private static Map<String, Integer> hand(String kind, Player actor) {
        return switch (kind) {
            case "ac" -> actor.getActionCards();
            case "so" -> actor.getSecretsUnscored();
            case "pn" -> actor.getPromissoryNotes();
            default -> null;
        };
    }

    @Nullable
    private static String seatAttribute(
            String seatName, String attribute, Function<String, Player> seats, List<String> problems) {
        Player seat = seats.apply(seatName);
        if (seat == null) {
            problems.add("no seat `" + seatName + "`");
            return null;
        }
        return switch (attribute) {
            case "faction" -> seat.getFaction();
            case "color" -> seat.getColor();
            default -> {
                problems.add("unknown seat attribute `" + attribute + "`; use " + SEAT_ATTRIBUTES);
                yield null;
            }
        };
    }

    public static void validate(String text, String label, List<String> errors) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            String head = matcher.group(1);
            String tail = matcher.group(3);
            if (":".equals(matcher.group(2))) {
                if (!CARD_KINDS.contains(head)) {
                    errors.add(label + ": unknown card kind in `" + matcher.group() + "`; use " + CARD_KINDS + ".");
                } else if (!isKnownCard(head, tail)) {
                    errors.add(label + ": unknown " + head + " id `" + tail + "`.");
                }
            } else if (!SEAT_ATTRIBUTES.contains(tail)) {
                errors.add(
                        label + ": unknown seat attribute in `" + matcher.group() + "`; use " + SEAT_ATTRIBUTES + ".");
            }
        }
    }

    public record CardReference(String kind, String cardId) {}

    public static List<CardReference> cardReferences(String text) {
        List<CardReference> cards = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            if (":".equals(matcher.group(2))) cards.add(new CardReference(matcher.group(1), matcher.group(3)));
        }
        return cards;
    }

    public static List<String> referencedSeats(String text) {
        List<String> seats = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            if (".".equals(matcher.group(2))) seats.add(matcher.group(1));
        }
        return seats;
    }

    static boolean isKnownCard(String kind, String cardId) {
        return switch (kind) {
            case "ac" -> Mapper.isValidActionCard(cardId);
            case "so" -> Mapper.isValidSecretObjective(cardId);
            case "pn" -> Mapper.isValidPromissoryNote(cardId) || cardId.contains("_");
            default -> false;
        };
    }
}
