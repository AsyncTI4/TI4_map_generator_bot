package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;

@UtilityClass
public class WinRace {

    private static final String ACTION_PHASE = "action";
    private static final int DURING_ACTION_PHASE = 0;
    private static final int LEADERSHIP_TOKENS = 3;
    private static final int MECATOL_POINT = 1;

    public static List<Player> winnersBefore(Game game, Player seat) {
        if (game.isFowMode() || game.getVp() <= 0 || !ACTION_PHASE.equalsIgnoreCase(game.getPhaseOfGame())) {
            return List.of();
        }
        return DecisionMemo.remember(() -> computeWinnersBefore(game, seat), "win.race", seat, game.getRound());
    }

    private static List<Player> computeWinnersBefore(Game game, Player seat) {
        OptionalInt mine = finish(game, seat);
        return game.getRealPlayers().stream()
                .filter(other -> other != seat && !other.isEliminated())
                .filter(other -> finishesFirst(finish(game, other), mine))
                .toList();
    }

    private static boolean finishesFirst(OptionalInt theirs, OptionalInt mine) {
        return theirs.isPresent() && (mine.isEmpty() || theirs.getAsInt() < mine.getAsInt());
    }

    static OptionalInt finish(Game game, Player player) {
        int missing = game.getVp() - player.getTotalVictoryPoints();
        if (missing <= 0) return OptionalInt.of(DURING_ACTION_PHASE);
        List<Integer> publics = readyPublics(game, player);
        boolean imperial = ScoringStakes.holdsUnplayedImperial(game, player);
        int duringAction = 0;
        if (imperial) {
            duringAction = best(publics, 0) + (player.controlsMecatol(true) ? MECATOL_POINT : 0);
            if (duringAction >= missing) return OptionalInt.of(DURING_ACTION_PHASE);
        }
        int byStatus = duringAction + best(publics, imperial ? 1 : 0);
        return byStatus >= missing ? OptionalInt.of(1 + player.getLowestSC()) : OptionalInt.empty();
    }

    private static int best(List<Integer> descending, int index) {
        return descending.size() > index ? descending.get(index) : 0;
    }

    static List<Integer> readyPublics(Game game, Player player) {
        if (!Helper.canPlayerScorePOs(game, player)) return List.of();
        Wallet wallet = projectedWallet(game, player);
        List<Integer> points = new ArrayList<>();
        int bestSpend = 0;
        for (String id : game.getRevealedPublicObjectives().keySet()) {
            if (ObjectivePolicy.hasScored(game, player, id) || !qualifies(game, player, wallet, id)) continue;
            int value = ObjectivePolicy.victoryPoints(id);
            if (ObjectiveCatalog.isSpend(id)) bestSpend = Math.max(bestSpend, value);
            else points.add(value);
        }
        if (bestSpend > 0) points.add(bestSpend);
        points.sort(Comparator.reverseOrder());
        return points;
    }

    private static boolean qualifies(Game game, Player player, Wallet wallet, String id) {
        SpendCost cost = ObjectiveCatalog.spendCost(id).orElse(SpendCost.NONE);
        if (isTokenSpend(cost)) return wallet.tokens() >= cost.tokens();
        return ObjectivePolicy.qualifies(game, player, wallet, id);
    }

    private static boolean isTokenSpend(SpendCost cost) {
        return cost.tokens() > 0 && cost.resources() == 0 && cost.influence() == 0 && cost.tradeGoods() == 0;
    }

    private static Wallet projectedWallet(Game game, Player player) {
        Wallet now = Wallet.of(game, player);
        int gained = holdsUnplayedLeadership(game, player) ? LEADERSHIP_TOKENS : 0;
        return new Wallet(
                now.planets(),
                now.tradeGoods(),
                now.tacticTokens(),
                now.strategyTokens() + gained,
                now.tradeGoodValue(),
                now.pooling());
    }

    private static boolean holdsUnplayedLeadership(Game game, Player player) {
        return player.getSCs().stream()
                .anyMatch(card -> StrategyCard.of(game, card) == StrategyCard.LEADERSHIP
                        && !game.getPlayedSCs().contains(card));
    }
}
