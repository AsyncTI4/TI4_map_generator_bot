package ti4.ai.strategy;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.StrategyCard;
import ti4.ai.letnev.GravleashPlan;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.strategycard.PickStrategyCardService;

@UtilityClass
public class StrategyCardRanking {

    public record Pick(PromptButton button, CardPlan plan, double value, double race) {}

    public static Optional<PromptButton> best(Game game, Player seat, List<PromptButton> pickButtons) {
        return bestPick(game, seat, pickButtons).map(Pick::button);
    }

    public static Optional<Pick> bestPick(Game game, Player seat, List<PromptButton> pickButtons) {
        return ranked(game, seat, pickButtons).stream().findFirst();
    }

    public static List<Pick> ranked(Game game, Player seat, List<PromptButton> pickButtons) {
        try (Thinking.Span ignored = Thinking.enter("strategy.bestPick")) {
            return rank(game, seat, pickButtons);
        }
    }

    private static List<Pick> rank(Game game, Player seat, List<PromptButton> pickButtons) {
        List<PromptButton> offered =
                pickButtons.stream().filter(button -> initiative(button) > 0).toList();
        if (offered.isEmpty()) return List.of();
        List<CardPlan> plans = StrategyCardPlanner.planAll(
                game,
                seat,
                offered.stream().map(StrategyCardRanking::initiative).toList(),
                PlanMoment.PICK);
        return java.util.stream.IntStream.range(0, offered.size())
                .mapToObj(index -> pick(game, seat, offered.get(index), plans.get(index)))
                .sorted(Comparator.comparingDouble(Pick::value)
                        .reversed()
                        .thenComparing(pick -> pick.plan().initiative()))
                .toList();
    }

    public static double value(Game game, Player seat, int initiative) {
        return value(game, seat, plan(game, seat, initiative));
    }

    public static double baseValue(Game game, Player seat, int initiative) {
        return baseValue(game, seat, plan(game, seat, initiative));
    }

    public static CardPlan pickPlan(Game game, Player seat, int initiative) {
        return plan(game, seat, initiative);
    }

    public static int initiative(PromptButton button) {
        String number = StringUtils.substringAfterLast(button.handlerId(), "_");
        return StringUtils.isNumeric(number) ? Integer.parseInt(number) : -1;
    }

    public static boolean imperialScoresNow(Game game, Player seat) {
        return seat.controlsMecatol(true)
                || !ObjectivePolicy.scorablePublics(game, seat).isEmpty();
    }

    private static CardPlan plan(Game game, Player seat, int initiative) {
        return StrategyCardPlanner.plan(PlanInputs.of(game, seat, PlanMoment.PICK), initiative);
    }

    private static Pick pick(Game game, Player seat, PromptButton button, CardPlan plan) {
        double race = InitiativeRace.gain(game, seat, plan.initiative());
        return new Pick(button, plan, value(game, seat, plan) + race, race);
    }

    private static double value(Game game, Player seat, CardPlan plan) {
        double denial = plan.card() == StrategyCard.IMPERIAL ? imperialDenial(game, seat) : 0;
        return baseValue(game, seat, plan)
                + GravleashPlan.cardBonus(game, seat, plan.card())
                + denial
                + CardValueScale.TRADE_GOOD * game.getScTradeGoods().getOrDefault(plan.initiative(), 0);
    }

    private static double baseValue(Game game, Player seat, CardPlan plan) {
        return plan.value() + PickPrior.nudge(seat.getFaction(), plan.card(), game.getRound());
    }

    static double imperialDenial(Game game, Player seat) {
        List<Player> order = PickStrategyCardService.getSCPickOrder(game);
        int seatIndex = order.indexOf(seat);
        if (seatIndex < 0) return 0;
        return order.subList(seatIndex + 1, order.size()).stream()
                .filter(other -> other.getSCs().size() < game.getStrategyCardsPerPlayer())
                .filter(other -> other.controlsMecatol(true))
                .mapToDouble(other -> CardValueScale.DENIAL_SHARE
                        * CardValueScale.VICTORY_POINT
                        * (closeToWinning(game, other) ? 1 : CardValueScale.DISTANT_DENIAL_SHARE))
                .max()
                .orElse(0);
    }

    private static boolean closeToWinning(Game game, Player player) {
        return player.getTotalVictoryPoints() >= game.getVp() - CardValueScale.DENIAL_CLOSE_GAP;
    }
}
