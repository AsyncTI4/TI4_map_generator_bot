package ti4.ai.secrets;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;

@UtilityClass
public class SecretCostRules {

    private static final String COST_KEY = "secretCost|";
    private static final String HERETICAL_WORKS = "dhw";
    private static final int HERETICAL_WORKS_FRAGMENTS = 2;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;
    private static final List<String> PURGE_PREFIXES = List.of("purge_Frags_", "purgeSupermassiveFrag_");

    public static void expect(AiTurnContext context, String secretId) {
        if (SpyNetwork.ID.equals(secretId) || HERETICAL_WORKS.equals(secretId)) {
            context.memory().put(COST_KEY + secretId, String.valueOf(context.now()));
        }
    }

    public static Optional<AiDecision> pay(AiTurnContext context) {
        return discardForSpyNetwork(context).or(() -> purgeForHereticalWorks(context));
    }

    private static Optional<AiDecision> discardForSpyNetwork(AiTurnContext context) {
        Optional<long[]> cost = cost(context, SpyNetwork.ID);
        if (cost.isEmpty()) return Optional.empty();
        if (context.seat().getAcCount() <= cost.get()[1]) {
            context.memory().remove(COST_KEY + SpyNetwork.ID);
            return Optional.empty();
        }
        long since = cost.get()[0] - CLOCK_SKEW_MILLIS;
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            Optional<PromptButton> discard = ActionCardValue.worstDiscard(
                    context.game(), context.seat(), prompt, "ac_discard_from_hand_", "retain", button -> true);
            if (discard.isPresent()) {
                return Optional.of(AiDecision.press(prompt, discard.get(), "discard an action card for a secret"));
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> purgeForHereticalWorks(AiTurnContext context) {
        Optional<long[]> cost = cost(context, HERETICAL_WORKS);
        if (cost.isEmpty()) return Optional.empty();
        String faction = context.faction();
        long since = cost.get()[0] - CLOCK_SKEW_MILLIS;
        boolean paid = context.seat().getFragments().size() <= cost.get()[1];
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.createdAtMillis() < since) continue;
            Optional<PromptButton> purge = prompt.firstEnabled(button ->
                    button.isOwnedBy(faction) && PURGE_PREFIXES.stream().anyMatch(button.handlerId()::startsWith));
            Optional<PromptButton> done = prompt.firstEnabled(button -> button.isOwnedBy(faction)
                    && "deleteButtons".equals(button.handlerId())
                    && button.label().startsWith("Done Purging"));
            if (done.isEmpty()) continue;
            if (!paid && purge.isPresent()) {
                return Optional.of(AiDecision.press(prompt, purge.get(), "purge a relic fragment for a secret"));
            }
            context.memory().remove(COST_KEY + HERETICAL_WORKS);
            return Optional.of(AiDecision.press(prompt, done.get(), "finish purging fragments"));
        }
        if (paid) context.memory().remove(COST_KEY + HERETICAL_WORKS);
        return Optional.empty();
    }

    private static Optional<long[]> cost(AiTurnContext context, String secretId) {
        Optional<String> value = context.memory().get(COST_KEY + secretId);
        if (value.isEmpty()) return Optional.empty();
        Player seat = context.seat();
        if (seat.getSecretsUnscored().containsKey(secretId)) return Optional.empty();
        String[] parts = value.get().split("\\|");
        if (!seat.getSecretsScored().containsKey(secretId) || !StringUtils.isNumeric(parts[0])) {
            context.memory().remove(COST_KEY + secretId);
            return Optional.empty();
        }
        long since = Long.parseLong(parts[0]);
        if (parts.length == 2 && StringUtils.isNumeric(parts[1]))
            return Optional.of(new long[] {since, Long.parseLong(parts[1])});
        int target = SpyNetwork.ID.equals(secretId)
                ? Math.max(0, seat.getAcCount() - SpyNetwork.CARDS)
                : Math.max(0, seat.getFragments().size() - HERETICAL_WORKS_FRAGMENTS);
        context.memory().put(COST_KEY + secretId, since + "|" + target);
        return Optional.of(new long[] {since, target});
    }
}
