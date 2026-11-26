package ti4.ai.xxcha;

import java.util.Optional;
import java.util.OptionalDouble;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.agenda.AgendaPolicy;
import ti4.ai.agenda.AgendaQueue;
import ti4.ai.agenda.DiscardBounties;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.eval.RelicValue;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.CardFacade;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaWhensAftersHelper;

@UtilityClass
public class QuashRules {

    private static final String ABILITY = "quash";
    private static final String SCEPTER = "emelpar";
    private static final int SCEPTER_VALUE_HORIZON = 6;
    private static final String QUEUE_QUASH = "queueWhen_ability_quash";
    private static final String OPEN_WHENS = "queueAWhen";
    private static final double EXPERT_QUASH_MARGIN = 1.0;
    private static final double MARGIN_PER_STRATEGY_TOKEN = 1.0 / 3;
    private static final double MIN_QUASH_MARGIN = -1.0;

    public static Optional<AiDecision> queue(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!canQuash(seat) || AgendaQueue.holdsQueuedWhen(game, seat) || !AgendaQueue.whenWindowOpen(game)) {
            return Optional.empty();
        }
        if (!worthQuashing(game, seat)) return Optional.empty();
        String paid = paysWithScepter(seat) ? ", paid with the Scepter of Emelpar" : "";
        return pressHidden(context, QUEUE_QUASH, "queue Quash to discard a bad agenda" + paid)
                .or(() -> pressHidden(context, OPEN_WHENS, "open its when options to Quash"));
    }

    public static boolean canQuash(Player seat) {
        return seat.hasAbility(ABILITY)
                && (seat.getStrategicCC() > 0 || paysWithScepter(seat))
                && !AgendaWhensAftersHelper.silencedByPoliticalSecret(seat);
    }

    public static boolean paysWithScepter(Player seat) {
        return seat.getStrategicCC() <= 0 && seat.hasRelicReady(SCEPTER);
    }

    static double paymentCost(Player seat) {
        if (!paysWithScepter(seat)) return CardFacade.strategyToken();
        Game game = seat.getGame();
        double roundsCovered = Math.max(1, SCEPTER_VALUE_HORIZON - game.getRound());
        return Math.min(CardFacade.strategyToken(), RelicValue.of(game, seat, SCEPTER) / roundsCovered);
    }

    public static boolean worthQuashing(Game game, Player seat) {
        OptionalDouble loss = AgendaPolicy.expectedLoss(game, seat);
        if (loss.isEmpty()) return AgendaPolicy.worthDiscarding(game, seat);
        return loss.getAsDouble() > threshold(seat) || paidEnough(game, seat);
    }

    private static boolean paidEnough(Game game, Player seat) {
        int bounty = DiscardBounties.quashBounty(game, seat);
        if (bounty <= 0) return false;
        OptionalDouble loss = AgendaPolicy.signedLoss(game, seat);
        return loss.isPresent() && loss.getAsDouble() + bounty > threshold(seat);
    }

    public static int bountyNeeded(Player quasher, double quasherLoss) {
        return Math.max(1, (int) Math.floor(threshold(quasher) - quasherLoss) + 1);
    }

    private static double threshold(Player seat) {
        return paymentCost(seat) + quashMargin(seat);
    }

    public static Optional<AiDecision> awaitBounty(AiTurnContext context) {
        if (!canQuash(context.seat())) return Optional.empty();
        return DiscardBounties.awaitOffers(context);
    }

    private static double quashMargin(Player seat) {
        if (!AiExperiments.enabled(seat, Experiment.AGENDA_VALUE_TABLE)) return 0;
        return Math.max(MIN_QUASH_MARGIN, EXPERT_QUASH_MARGIN - MARGIN_PER_STRATEGY_TOKEN * seat.getStrategicCC());
    }

    private static Optional<AiDecision> pressHidden(AiTurnContext context, String handlerId, String reason) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> button = prompt.enabledHandler(handlerId);
            if (button.isPresent() && !context.alreadyPressed(prompt, button.get())) {
                return Optional.of(AiDecision.press(prompt, button.get(), reason));
            }
        }
        return Optional.empty();
    }
}
