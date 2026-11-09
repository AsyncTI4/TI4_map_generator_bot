package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.brain.StrategyCard;
import ti4.ai.eval.FractureCampaign;
import ti4.ai.eval.ImaginedTurn;
import ti4.ai.secrets.SecretOdds;
import ti4.ai.secrets.SecretValue;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;

@UtilityClass
public class WinRace {

    private static final String ACTION_PHASE = "action";
    private static final int IMMEDIATELY = -1;
    private static final int DURING_ACTION_PHASE = 0;
    private static final int LEADERSHIP_TOKENS = 3;
    private static final int MECATOL_POINT = 1;
    private static final int STYX_POINT = 1;

    public static List<Player> winnersBefore(Game game, Player seat) {
        if (game.isFowMode() || game.getVp() <= 0 || !ACTION_PHASE.equalsIgnoreCase(game.getPhaseOfGame())) {
            return List.of();
        }
        return DecisionMemo.remember(() -> computeWinnersBefore(game, seat), "win.race", seat, game.getRound());
    }

    private static List<Player> computeWinnersBefore(Game game, Player seat) {
        boolean secrets = countsSecrets(seat);
        OptionalInt mine = secrets ? finish(game, seat, ownSecret(game, seat)) : finish(game, seat);
        return game.getRealPlayers().stream()
                .filter(other -> other != seat && !other.isEliminated())
                .filter(other -> finishesFirst(rivalFinish(game, seat, other, secrets), mine))
                .toList();
    }

    private static boolean countsSecrets(Player seat) {
        return AiExperiments.enabled(seat, AiExperiments.Experiment.WIN_RACE_SECRETS);
    }

    private static OptionalInt rivalFinish(Game game, Player seat, Player rival, boolean secrets) {
        return secrets ? finish(game, rival, fearedAtStatus(game, seat, rival)) : finish(game, rival);
    }

    private static Boost ownSecret(Game game, Player seat) {
        boolean knowsHand = !ImaginedTurn.imagines(seat);
        int secret =
                knowsHand && SecretValue.bestScorableStatusSecret(game, seat).isPresent() ? 1 : 0;
        return new Boost(0, 0, 0, false, secret);
    }

    private static Boost fearedAtStatus(Game game, Player viewer, Player rival) {
        int secret = (int) Math.round(SecretOdds.chanceReady(game, viewer, rival));
        return new Boost(0, styxPoint(game, rival), 0, ScoringPaths.imperialAwaitsMecatol(game, rival), secret);
    }

    public static boolean winsWithReserve(Game game, Player seat, String reservedId) {
        OptionalInt mine = finish(game, seat, ownSecret(game, seat), readyPublics(game, seat, reservedId));
        return winnersBefore(game, seat).stream()
                .allMatch(rival -> finishesBefore(mine, rivalFinish(game, seat, rival, true)));
    }

    private static boolean finishesFirst(OptionalInt theirs, OptionalInt mine) {
        return theirs.isPresent() && (mine.isEmpty() || theirs.getAsInt() < mine.getAsInt());
    }

    public record Boost(
            int immediatePoints, int actionPoints, int extraPublic, boolean takesMecatol, int statusSecret) {

        public static final Boost NONE = new Boost(0, 0, 0, false, 0);

        public Boost(int immediatePoints, int actionPoints, int extraPublic, boolean takesMecatol) {
            this(immediatePoints, actionPoints, extraPublic, takesMecatol, 0);
        }
    }

    static OptionalInt finish(Game game, Player player) {
        return finish(game, player, Boost.NONE);
    }

    public static OptionalInt finish(Game game, Player player, Boost boost) {
        return finish(game, player, boost, null);
    }

    private static OptionalInt finish(Game game, Player player, Boost boost, List<Integer> ready) {
        int missing = game.getVp() - player.getTotalVictoryPoints() - boost.immediatePoints();
        if (missing <= 0) return OptionalInt.of(boost.immediatePoints() > 0 ? IMMEDIATELY : DURING_ACTION_PHASE);
        List<Integer> publics = new ArrayList<>(ready != null ? ready : readyPublics(game, player));
        if (boost.extraPublic() > 0) {
            publics.add(boost.extraPublic());
            publics.sort(Comparator.reverseOrder());
        }
        boolean imperial = ScoringStakes.holdsUnplayedImperial(game, player);
        boolean mecatol = player.controlsMecatol(true) || boost.takesMecatol();
        int duringAction = boost.actionPoints();
        if (imperial) duringAction += best(publics, 0) + (mecatol ? MECATOL_POINT : 0);
        if (duringAction >= missing) return OptionalInt.of(DURING_ACTION_PHASE);
        int byStatus = duringAction + best(publics, imperial ? 1 : 0) + Math.min(1, boost.statusSecret());
        return byStatus >= missing ? OptionalInt.of(1 + player.getInitiative()) : OptionalInt.empty();
    }

    public static Boost feared(Game game, Player viewer, Player rival, int immediatePoints) {
        int actionPoints = (int) Math.round(SecretOdds.chanceReady(game, viewer, rival)) + styxPoint(game, rival);
        return new Boost(immediatePoints, actionPoints, 0, ScoringPaths.imperialAwaitsMecatol(game, rival));
    }

    private static int styxPoint(Game game, Player rival) {
        return FractureCampaign.dock(game, rival).isPresent() ? STYX_POINT : 0;
    }

    public static boolean canWinThisRound(Game game, Player viewer, Player player) {
        return DecisionMemo.remember(
                () -> finish(game, player, feared(game, viewer, player, 0)).isPresent(),
                "win.canWinThisRound",
                viewer,
                player,
                game.getRound());
    }

    public static boolean finishesBefore(OptionalInt mine, OptionalInt theirs) {
        return mine.isPresent() && (theirs.isEmpty() || mine.getAsInt() < theirs.getAsInt());
    }

    private static int best(List<Integer> descending, int index) {
        return descending.size() > index ? descending.get(index) : 0;
    }

    static List<Integer> readyPublics(Game game, Player player) {
        return readyPublics(game, player, null);
    }

    private static List<Integer> readyPublics(Game game, Player player, String reservedId) {
        if (!Helper.canPlayerScorePOs(game, player)) return List.of();
        Wallet wallet = projectedWallet(game, player);
        List<Integer> points = new ArrayList<>();
        int bestSpend = 0;
        for (String id : game.getRevealedPublicObjectives().keySet()) {
            if (ObjectivePolicy.hasScored(game, player, id)) continue;
            int value = ObjectivePolicy.victoryPoints(id);
            boolean spend = ObjectiveCatalog.isSpend(id);
            if (reservedId != null && spend) {
                if (id.equals(reservedId)) bestSpend = value;
                continue;
            }
            if (!qualifies(game, player, wallet, id)) continue;
            if (spend) bestSpend = Math.max(bestSpend, value);
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
