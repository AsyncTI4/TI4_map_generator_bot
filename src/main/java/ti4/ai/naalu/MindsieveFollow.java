package ti4.ai.naalu;

import java.util.Comparator;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.brain.StrategyCard;
import ti4.ai.perception.AiPrompt;
import ti4.ai.promissory.NoteGiving;
import ti4.ai.strategy.StrategyTokenCost;
import ti4.ai.trade.TradeRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class MindsieveFollow {

    static final String BREAKTHROUGH = "naalubt";
    static final String BUTTON = "mindsieveFollow_";
    private static final String KEY = "mindsieveFollow|";
    private static final String SEPARATOR = "_";

    record Gift(Match match, String note, double cost) {}

    public static boolean pending(AiTurnContext context, int initiative) {
        return context.memory().has(key(context.game(), initiative));
    }

    public static boolean covers(AiTurnContext context, StrategyCard card) {
        Game game = context.game();
        return game.getPlayedSCs().stream()
                .filter(initiative -> StrategyCard.of(game, initiative) == card)
                .anyMatch(initiative -> pending(context, initiative)
                        || cheapestGift(context, initiative).isPresent());
    }

    public static Optional<AiDecision> giveNoteFirst(AiTurnContext context, int initiative, AiDecision decision) {
        if (!(decision instanceof AiDecision.Press) || pending(context, initiative)) return Optional.empty();
        return cheapestGift(context, initiative)
                .map(found -> found.match()
                        .press("follow with Mindsieve by giving " + found.note() + " instead of a strategy token"));
    }

    public static void remember(AiTurnContext context, int initiative) {
        context.memory().put(key(context.game(), initiative), String.valueOf(context.now()));
    }

    static Optional<Gift> cheapestGift(AiTurnContext context, int initiative) {
        Game game = context.game();
        Player seat = context.seat();
        if (!seat.hasUnlockedBreakthrough(BREAKTHROUGH) || StrategyCard.of(game, initiative) == StrategyCard.TRADE) {
            return Optional.empty();
        }
        Player primary = game.getPlayerFromSC(initiative);
        if (primary == null || primary == seat) return Optional.empty();
        double token = tokenWorth(game, seat);
        String prefix = BUTTON + initiative + SEPARATOR;
        return Prompts.newestFirst(context.prompts()).stream()
                .filter(AiPrompt::isHidden)
                .flatMap(prompt -> prompt.enabledButtons().stream()
                        .filter(button ->
                                button.isUnowned() && button.handlerId().startsWith(prefix))
                        .filter(button -> !context.alreadyPressed(prompt, button))
                        .map(button -> new Match(prompt, button)))
                .map(match -> gift(game, seat, primary, match, prefix))
                .filter(gift -> gift.cost() <= token)
                .min(Comparator.comparingDouble(Gift::cost).thenComparing(Gift::note));
    }

    static double tokenWorth(Game game, Player seat) {
        return StrategyTokenCost.toSpend(game, seat).orElse(StrategyTokenCost.UNSPARED);
    }

    private static Gift gift(Game game, Player seat, Player primary, Match match, String prefix) {
        String note = StringUtils.removeStart(match.button().handlerId(), prefix);
        boolean giveable = seat.getPromissoryNotes().containsKey(note)
                && !seat.getPromissoryNotesInPlayArea().contains(note)
                && !NoteGiving.withheld(seat, note)
                && !NoteGiving.isThroneOrAlliance(note);
        double cost = giveable ? TradeRules.noteGiveCost(game, seat, primary, note) : Double.POSITIVE_INFINITY;
        return new Gift(match, note, cost);
    }

    private static String key(Game game, int initiative) {
        return KEY + game.getRound() + "|" + initiative;
    }
}
