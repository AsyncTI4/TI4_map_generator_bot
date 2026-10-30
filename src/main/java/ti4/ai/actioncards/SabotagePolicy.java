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
import ti4.model.ActionCardModel;
import ti4.model.DeckModel;
import ti4.model.Mapper;
import ti4.service.actioncard.SabotageService;

@UtilityClass
public class SabotagePolicy {

    static final double CONTENDER_RESERVE = 4.0;
    static final double OVERRULE_RESERVE = 3.0;
    static final String OVERRULE = "overrule";
    private static final String OVERRULE_TITLE = "Overrule";
    static final String SABOTAGE_PREFIX = "sabotage_ac_";
    private static final String SEPARATOR = "_";

    record Cancellation(PromptButton button, String title, Player cardPlayer) {}

    record Choice(AiPrompt prompt, PromptButton button, String reason, boolean instinctTraining) {}

    public static Optional<PromptButton> cancelWith(AiTurnContext context, AiPrompt window) {
        return cancellation(context, window)
                .filter(cancellation -> mayCancelWithCard(context))
                .filter(cancellation -> worthCancelling(context, cancellation))
                .map(Cancellation::button);
    }

    static Optional<Choice> choose(AiTurnContext context, AiPrompt window) {
        Optional<Cancellation> cancellation = cancellation(context, window);
        if (cancellation.isEmpty()) return Optional.empty();
        Cancellation cancel = cancellation.get();
        double value = cancelValue(context, cancel.title(), cancel.cardPlayer());
        Optional<InstinctTraining.Use> token = InstinctTraining.available(context, cancel.title(), cancel.cardPlayer())
                .filter(use -> value >= use.cost());
        Optional<PromptButton> card = Optional.of(cancel)
                .filter(candidate -> mayCancelWithCard(context))
                .filter(candidate -> worthCancelling(context, candidate))
                .map(Cancellation::button);
        double cardKeep = marginalKeep(context.game(), context.seat(), cancel.title());
        if (token.isPresent() && (card.isEmpty() || token.get().cost() < cardKeep)) {
            return Optional.of(new Choice(
                    token.get().prompt(), token.get().button(), "cancel the action card with Instinct Training", true));
        }
        return card.map(button -> new Choice(window, button, "cancel the action card with a Sabotage", false));
    }

    public static boolean aimedAtSeat(AiTurnContext context, AiPrompt window) {
        return cancellation(context, window)
                .filter(cancellation -> mayCancelWithCard(context)
                        || InstinctTraining.available(context, cancellation.title(), cancellation.cardPlayer())
                                .isPresent())
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
        double rival = CardScoring.rivalStake(title, game, seat, cardPlayer);
        double denied = Involvement.aimedAtSeat(context, title, cardPlayer)
                ? ScoringStakes.scoreValue(game, seat, CardScoring.pointsDenied(title, game, seat))
                : 0;
        return baseline + rival + denied;
    }

    static boolean overruleUnseen(Game game, Player seat) {
        if (game.isOverrulePurged() || seat.getActionCards().containsKey(OVERRULE)) return false;
        DeckModel deck = Mapper.getDeck(game.getAcDeckID());
        if (deck == null || !deck.getCardIDs().contains(OVERRULE)) return false;
        return !game.getDiscardActionCards().containsKey(OVERRULE)
                && game.getGameStats().getActionCardPlays().stream()
                        .noneMatch(play -> OVERRULE_TITLE.equals(play.getActionCard()));
    }

    static double marginalKeep(Game game, Player seat) {
        return marginalKeep(game, seat, "");
    }

    static double marginalKeep(Game game, Player seat, String title) {
        List<String> held = seat.getActionCards().keySet().stream()
                .filter(AiActionCards::isSabotage)
                .sorted()
                .toList();
        if (held.isEmpty()) return Double.MAX_VALUE;
        double prior = ActionCardPrior.keepPrior(held.getFirst());
        if (held.size() > 1) return ActionCardValue.EXTRA_SABOTAGE_SHARE * prior;
        return Math.max(prior, reserve(game, seat, title));
    }

    static double reserve(Game game, Player seat, String title) {
        double reserve = ScoringStakes.anyOpponentIsContender(game, seat) ? CONTENDER_RESERVE : 0;
        if (!OVERRULE_TITLE.equals(title) && overruleUnseen(game, seat)) reserve = Math.max(reserve, OVERRULE_RESERVE);
        return reserve;
    }

    private static boolean worthCancelling(AiTurnContext context, Cancellation cancellation) {
        double value = cancelValue(context, cancellation.title(), cancellation.cardPlayer());
        return value >= marginalKeep(context.game(), context.seat(), cancellation.title());
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

    private static boolean mayCancelWithCard(AiTurnContext context) {
        Player seat = context.seat();
        boolean holdsSabotage = seat.getActionCards().keySet().stream().anyMatch(AiActionCards::isSabotage);
        return holdsSabotage && ActionCardRules.mayPlay(context.game(), seat);
    }

    private static boolean mayCancel(AiTurnContext context, String title, Player cardPlayer) {
        Game game = context.game();
        Player seat = context.seat();
        if (SabotageService.isShameixBaneCancellationBlocked(seat, cardPlayer)) return false;
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
