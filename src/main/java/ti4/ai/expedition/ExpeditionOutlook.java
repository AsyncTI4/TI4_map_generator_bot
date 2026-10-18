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

    public static List<Choice> options(Game game, Player seat, Holdings holdings) {
        if (!isOpen(game, seat)) return List.of();
        double breakthrough = BreakthroughValue.of(game, seat) + BreakthroughValue.fractureRoll(game, seat);
        double thundersEdge = ThundersEdgeRace.gainFromClaiming(game, seat);
        return Arrays.stream(ExpeditionKind.values())
                .filter(kind -> !kind.isClaimed(game))
                .map(kind -> choice(game, seat, kind, holdings, breakthrough, thundersEdge))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingDouble(Choice::net).reversed())
                .toList();
    }

    private static Optional<Choice> choice(
            Game game, Player seat, ExpeditionKind kind, Holdings holdings, double breakthrough, double thundersEdge) {
        return KindPricing.of(game, seat, kind, holdings).map(price -> {
            double nudge = ExpeditionStats.kindNudge(seat.getFaction(), kind);
            double withoutUrgency = breakthrough + thundersEdge + nudge - price.cost();
            double urgency = withoutUrgency > 0 ? RaceUrgency.of(game, seat, kind) : 0;
            return new Choice(kind, price.payment(), price.cost(), breakthrough, thundersEdge, nudge, urgency);
        });
    }
}
