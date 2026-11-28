package ti4.ai.naalu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.strategy.CardFacade;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class ZeuAgentRules {

    static final String AGENT = "naaluagent-te";
    static final String USE_PREFIX = "exhaustAgent_" + AGENT + "_";
    static final double MIN_UNLOCK_GAIN = 1.0;
    private static final String DECLINE = "deleteButtons";

    record Offer(Match match, String position, String tokenOwner) {

        boolean createdThisTurn(AiTurnContext context) {
            return Prompts.createdThisTurn(context, match.prompt());
        }
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        if (!context.seat().hasUnexhaustedLeader(AGENT)) return Optional.empty();
        for (Offer offer : offers(context)) {
            Optional<AiDecision> answer = answer(context, offer);
            if (answer.isPresent()) return answer;
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> answer(AiTurnContext context, Offer offer) {
        if (!offer.tokenOwner().equals(context.faction())) {
            return decline(offer, "leave another player's command token where it is");
        }
        if (!offer.createdThisTurn(context)) return decline(offer, "the moment to return the token has passed");
        Optional<AiDecision> unusable = unusable(context, offer);
        if (unusable.isPresent()) return unusable;
        if (ownActivation(context, offer)) {
            return Optional.of(offer.match().press("use Z'eu so the system it just activated and its fleet stay free"));
        }
        if (CardFacade.tokenUnlockGain(context.game(), context.seat(), offer.position()) < MIN_UNLOCK_GAIN) {
            return decline(offer, "keep Z'eu for a better token");
        }
        return Optional.of(offer.match().press("use Z'eu to free a system its command token locks"));
    }

    private static boolean ownActivation(AiTurnContext context, Offer offer) {
        return context.isActivePlayer()
                && offer.position().equals(context.game().getActiveSystem());
    }

    private static Optional<AiDecision> unusable(AiTurnContext context, Offer offer) {
        Player seat = context.seat();
        Tile tile = context.game().getTileByPosition(offer.position());
        if (tile == null || !tile.hasPlayerCC(seat)) return decline(offer, "the token is already gone");
        if (seat.getTacticalCC() <= 0) return decline(offer, "no tactic token left to activate it again");
        return Optional.empty();
    }

    private static Optional<AiDecision> decline(Offer offer, String why) {
        AiPrompt prompt = offer.match().prompt();
        return prompt.enabledHandler(DECLINE).map(button -> AiDecision.press(prompt, button, "decline Z'eu: " + why));
    }

    private static List<Offer> offers(AiTurnContext context) {
        List<Offer> offers = new ArrayList<>();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            prompt.firstEnabled(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(USE_PREFIX)
                            && !context.alreadyPressed(prompt, button))
                    .ifPresent(button -> {
                        String payload = StringUtils.removeStart(button.handlerId(), USE_PREFIX);
                        offers.add(new Offer(
                                new Match(prompt, button),
                                StringUtils.substringBefore(payload, "_"),
                                StringUtils.substringAfter(payload, "_")));
                    });
        }
        return offers;
    }
}
