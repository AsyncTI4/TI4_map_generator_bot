package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.actioncards.CardPrompts.Offer;
import ti4.ai.brain.AiTurnContext;

@UtilityClass
class CardChains {

    static final String PARTS = "|";
    private static final String PARTS_REGEX = "[|]";

    enum Source {
        MENTIONED,
        ANYONE,
        OWNED
    }

    @FunctionalInterface
    interface ChoiceValue {
        double of(AiTurnContext context, String suffix);
    }

    record Link(String prefix, Source source, ChoiceValue value, boolean last, String reason) {}

    static Link next(String prefix, Source source, ChoiceValue value, String reason) {
        return new Link(prefix, source, value, false, reason);
    }

    static Link last(String prefix, Source source, ChoiceValue value, String reason) {
        return new Link(prefix, source, value, true, reason);
    }

    static String target(String... parts) {
        return String.join(PARTS, parts);
    }

    static TargetStep chain(Link... links) {
        return (context, key, play) -> {
            String[] planned = play.extra().split(PARTS_REGEX, -1);
            for (int index = links.length - 1; index >= 0; index--) {
                Link link = links[index];
                List<Offer> offers = offers(context, play, link).stream()
                        .filter(offer -> link.value().of(context, offer.suffix()) > Double.NEGATIVE_INFINITY)
                        .toList();
                String plan = index < planned.length ? planned[index] : "";
                Optional<Offer> choice =
                        CardPrompts.choose(offers, plan, suffix -> link.value().of(context, suffix));
                if (choice.isEmpty()) continue;
                if (link.last()) PlayMemory.moveTo(context, key, play, Stage.DONE);
                return choice.map(offer -> offer.match().press(link.reason() + " " + offer.suffix()));
            }
            return Optional.empty();
        };
    }

    private static List<Offer> offers(AiTurnContext context, CardPlay play, Link link) {
        return switch (link.source()) {
            case MENTIONED -> CardPrompts.targetOffers(context, play, link.prefix());
            case ANYONE -> CardPrompts.unmentionedTargetOffers(context, play, link.prefix());
            case OWNED ->
                CardPrompts.ownedOffers(context, play.playedAt() - CardPrompts.CLOCK_SKEW_MILLIS, link.prefix());
        };
    }
}
