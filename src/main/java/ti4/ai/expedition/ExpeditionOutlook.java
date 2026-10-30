package ti4.ai.expedition;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ExpeditionOutlook {

    private static final String THUNDERS_EDGE = "thundersedge";

    public record Choice(
            ExpeditionKind kind,
            Wallet.Payment payment,
            double cost,
            double breakthrough,
            double thundersEdge,
            double nudge,
            double urgency) {

        public double gain() {
            return breakthrough + thundersEdge + nudge + urgency;
        }

        public double net() {
            return gain() - cost;
        }
    }

    public static boolean isOpen(Game game, Player seat) {
        return game.isThundersEdge()
                && !game.isTwilightsFallMode()
                && seat.isRealPlayer()
                && !seat.isPassed()
                && game.getExpeditions().getRemainingExpeditionCount() > 0
                && game.getTileFromPlanet(THUNDERS_EDGE) == null;
    }

    public static Optional<Choice> best(Game game, Player seat) {
        return best(game, seat, Holdings.of(game, seat));
    }

    public static Optional<Choice> best(Game game, Player seat, Holdings holdings) {
        return options(game, seat, holdings).stream()
                .filter(choice -> choice.net() > 0)
                .findFirst();
    }

    public static Optional<Choice> claimNow(Game game, Player seat) {
        Holdings holdings = Holdings.of(game, seat);
        if (BreakthroughValue.isPriority(seat) || isLastSlice(game)) return best(game, seat, holdings);
        double waiting = waitingValue(game, seat, holdings);
        return best(game, seat, holdings).filter(choice -> choice.net() - choice.urgency() >= waiting);
    }

    private static boolean isLastSlice(Game game) {
        return game.getExpeditions().getRemainingExpeditionCount() == 1;
    }

    public static double value(Game game, Player seat, Holdings holdings) {
        double now = options(game, seat, holdings).stream()
                .filter(choice -> choice.net() > 0)
                .mapToDouble(choice -> choice.net() * chanceOfReaching(game, seat, choice.kind(), holdings))
                .max()
                .orElse(0);
        return Math.max(now, waitingValue(game, seat, holdings));
    }

    private static double chanceOfReaching(Game game, Player seat, ExpeditionKind kind, Holdings holdings) {
        return holdings.racing() ? RaceUrgency.stillOpen(game, seat, kind) : 1.0;
    }

    private static double waitingValue(Game game, Player seat, Holdings holdings) {
        if (holdings.passing()) return 0;
        return options(game, seat, holdings.atPassing()).stream()
                .mapToDouble(
                        choice -> (choice.net() - choice.urgency()) * RaceUrgency.stillOpen(game, seat, choice.kind()))
                .filter(value -> value > 0)
                .max()
                .orElse(0);
    }

    public static List<Choice> options(Game game, Player seat, Holdings holdings) {
        if (!isOpen(game, seat)) return List.of();
        double breakthrough = BreakthroughValue.of(game, seat) + BreakthroughValue.fractureRoll(game, seat);
        double thundersEdge = ThundersEdgeRace.gainFromClaiming(game, seat);
        SpendHorizon horizon = SpendHorizon.of(game, seat, holdings);
        return Arrays.stream(ExpeditionKind.values())
                .filter(kind -> !kind.isClaimed(game))
                .map(kind -> choice(game, seat, kind, holdings, horizon, breakthrough, thundersEdge))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingDouble(Choice::net).reversed())
                .toList();
    }

    private static Optional<Choice> choice(
            Game game,
            Player seat,
            ExpeditionKind kind,
            Holdings holdings,
            SpendHorizon horizon,
            double breakthrough,
            double thundersEdge) {
        return KindPricing.of(game, seat, kind, holdings, horizon).map(price -> {
            double nudge = ExpeditionStats.kindNudge(seat.getFaction(), kind);
            double withoutUrgency = breakthrough + thundersEdge + nudge - price.cost();
            double urgency = withoutUrgency > 0 ? RaceUrgency.of(game, seat, kind) : 0;
            return new Choice(kind, price.payment(), price.cost(), breakthrough, thundersEdge, nudge, urgency);
        });
    }
}
