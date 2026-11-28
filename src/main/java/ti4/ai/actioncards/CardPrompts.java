package ti4.ai.actioncards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;

@UtilityClass
class CardPrompts {

    static final long CLOCK_SKEW_MILLIS = 5_000L;

    record Offer(Match match, String suffix) {}

    static Optional<Match> ownedPublic(AiTurnContext context, long since, Predicate<String> handler) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            Optional<PromptButton> button = prompt.firstEnabled(candidate -> candidate.isOwnedBy(context.faction())
                    && handler.test(candidate.handlerId())
                    && !context.alreadyPressed(prompt, candidate));
            if (button.isPresent()) return Optional.of(new Match(prompt, button.get()));
        }
        return Optional.empty();
    }

    static List<Match> ownedPublicAll(AiTurnContext context, long since, String prefix) {
        return ownedOffers(context, since, prefix).stream().map(Offer::match).toList();
    }

    static List<Offer> ownedOffers(AiTurnContext context, long since, String prefix) {
        List<Offer> offers = new ArrayList<>();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            for (PromptButton button : prompt.enabledButtons()) {
                if (button.isOwnedBy(context.faction())
                        && matches(button.handlerId(), prefix)
                        && !context.alreadyPressed(prompt, button)) {
                    offers.add(offer(prompt, button, prefix));
                }
            }
        }
        return offers;
    }

    static List<Offer> targetOffers(AiTurnContext context, CardPlay play, String prefix) {
        return unownedOffers(context, play.playedAt() - CLOCK_SKEW_MILLIS, prefix, true);
    }

    static List<Offer> unmentionedTargetOffers(AiTurnContext context, CardPlay play, String prefix) {
        return unownedOffers(context, play.since() - CLOCK_SKEW_MILLIS, prefix, false);
    }

    private static List<Offer> unownedOffers(AiTurnContext context, long since, String prefix, boolean mentioned) {
        List<Offer> offers = new ArrayList<>();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            if (mentioned && !Prompts.mentions(prompt, context.seat())) continue;
            for (PromptButton button : prompt.enabledButtons()) {
                if (button.isUnowned()
                        && matches(button.handlerId(), prefix)
                        && !context.alreadyPressed(prompt, button)) {
                    offers.add(offer(prompt, button, prefix));
                }
            }
        }
        return offers;
    }

    static boolean matches(String handlerId, String prefix) {
        boolean prefixed = prefix.endsWith("_") || prefix.endsWith("@");
        return prefixed ? handlerId.startsWith(prefix) : handlerId.equals(prefix);
    }

    static Optional<Offer> choose(List<Offer> offers, String planned, ToDoubleFunction<String> value) {
        Optional<Offer> plannedOffer = offers.stream()
                .filter(offer -> !planned.isBlank() && planned.equals(offer.suffix()))
                .findFirst();
        if (plannedOffer.isPresent()) return plannedOffer;
        return offers.stream()
                .max(Comparator.comparingDouble((Offer offer) -> value.applyAsDouble(offer.suffix()))
                        .thenComparing(Offer::suffix, Comparator.reverseOrder()));
    }

    private static Offer offer(AiPrompt prompt, PromptButton button, String prefix) {
        return new Offer(new Match(prompt, button), StringUtils.removeStart(button.handlerId(), prefix));
    }
}
