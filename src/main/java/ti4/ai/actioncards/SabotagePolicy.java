package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSettings;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.image.Mapper;
import ti4.model.ActionCardModel;
import ti4.service.actioncard.SabotageService;

@UtilityClass
public class SabotagePolicy {

    static final double CONTENDER_RESERVE = 4.0;
    static final String SABOTAGE_PREFIX = "sabotage_ac_";
    private static final String SEPARATOR = "_";

    record Cancellation(PromptButton button, String title, Player cardPlayer) {}

    public static Optional<PromptButton> cancelWith(AiTurnContext context, AiPrompt window) {
        return cancellation(context, window)
                .filter(cancellation -> worthCancelling(context, cancellation))
                .map(Cancellation::button);
    }

    public static boolean aimedAtSeat(AiTurnContext context, AiPrompt window) {
        return cancellation(context, window)
                .filter(cancellation ->
                        Involvement.aimedAtSeat(context, cancellation.title(), cancellation.cardPlayer()))
                .isPresent();
    }

    public static Optional<String> cardPlayerFaction(AiPrompt window) {
        return window.firstEnabled(SabotagePolicy::isCancelButton)
                .map(button -> StringUtils.substringAfterLast(button.handlerId(), SEPARATOR));
    }

    static double baseline(double cancelAppetite, double involvement, double standing) {
        return ActionCardPrior.AVERAGE_CARD * cancelAppetite * involvement * standing;
    }

    static double cancelValue(AiTurnContext context, String title, Player cardPlayer) {
        Game game = context.game();
        Player seat = context.seat();
        String alias = ActionCardPrior.aliasOfTitle(game, title).orElse(title);
        double baseline = baseline(
                ActionCardPrior.cancelAppetite(alias),
                Involvement.level(context, title, cardPlayer),
                TableStanding.of(game, seat, cardPlayer));
        double rival = ScoringStakes.rivalStake(game, cardPlayer, CardScoring.pointsFor(title, game, cardPlayer));
        double denied = Involvement.aimedAtSeat(context, title, cardPlayer)
                ? ScoringStakes.scoreValue(game, seat, CardScoring.pointsDenied(title, game, seat))
                : 0;
        return baseline + rival + denied;
    }

    static double marginalKeep(Game game, Player seat) {
        List<String> held = seat.getActionCards().keySet().stream()
                .filter(AiActionCards::isSabotage)
                .sorted()
                .toList();
        if (held.isEmpty()) return Double.MAX_VALUE;
        double prior = ActionCardPrior.keepPrior(held.getFirst());
        if (held.size() > 1) return ActionCardValue.EXTRA_SABOTAGE_SHARE * prior;
        return ScoringStakes.anyOpponentIsContender(game, seat) ? Math.max(prior, CONTENDER_RESERVE) : prior;
    }

    private static boolean worthCancelling(AiTurnContext context, Cancellation cancellation) {
        double value = cancelValue(context, cancellation.title(), cancellation.cardPlayer());
        return value >= marginalKeep(context.game(), context.seat());
    }

    private static Optional<Cancellation> cancellation(AiTurnContext context, AiPrompt window) {
        Game game = context.game();
        Player seat = context.seat();
        if (AiSettings.usesLegacyActionCards(seat) || game.isFowMode()) return Optional.empty();
        Optional<PromptButton> button = window.firstEnabled(SabotagePolicy::isCancelButton);
        if (button.isEmpty()) return Optional.empty();
        String id = StringUtils.removeStart(button.get().handlerId(), SABOTAGE_PREFIX);
        String title = StringUtils.substringBeforeLast(id, SEPARATOR);
        Player cardPlayer = game.getPlayerFromColorOrFaction(StringUtils.substringAfterLast(id, SEPARATOR));
        if (cardPlayer == null || cardPlayer == seat || !mayCancel(context, title, cardPlayer)) {
            return Optional.empty();
        }
        return Optional.of(new Cancellation(button.get(), title, cardPlayer));
    }

    private static boolean mayCancel(AiTurnContext context, String title, Player cardPlayer) {
        Game game = context.game();
        Player seat = context.seat();
        boolean holdsSabotage = seat.getActionCards().keySet().stream().anyMatch(AiActionCards::isSabotage);
        if (!holdsSabotage
                || !ActionCardRules.mayPlay(game, seat)
                || SabotageService.isShameixBaneCancellationBlocked(seat, cardPlayer)) {
            return false;
        }
        return ActionCardPrior.aliasOfTitle(game, title)
                .map(Mapper::getActionCard)
                .filter(model -> !ActionCardHelper.cannotBeSabotaged(model))
                .map(ActionCardModel::getName)
                .isPresent();
    }

    private static boolean isCancelButton(PromptButton button) {
        return button.isUnowned() && button.handlerId().startsWith(SABOTAGE_PREFIX);
    }
}
