package ti4.ai.scoring;

import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ExpectedReadying {

    public static final int DIPLOMACY_READIES = 2;

    public static List<Wallet.PlanetValue> beforeStatus(Game game, Player seat) {
        if (!expectsDiplomacy(game, seat)) return List.of();
        List<String> exhausted = seat.getExhaustedPlanets().stream()
                .filter(seat.getPlanets()::contains)
                .toList();
        return new Wallet(List.of(), 0, 0, 0, 1, Pooling.of(seat))
                .withPlanets(game, exhausted).planets().stream()
                        .sorted(Comparator.comparingInt(
                                        (Wallet.PlanetValue planet) -> -(planet.resources() + planet.influence()))
                                .thenComparing(Wallet.PlanetValue::name))
                        .limit(DIPLOMACY_READIES)
                        .toList();
    }

    private static boolean expectsDiplomacy(Game game, Player seat) {
        for (Player holder : game.getRealPlayers()) {
            for (int initiative : holder.getSCs()) {
                if (StrategyCard.of(game, initiative) != StrategyCard.DIPLOMACY) continue;
                if (game.getPlayedSCs().contains(initiative)) continue;
                if (holder == seat || seat.getStrategicCC() > 0) return true;
            }
        }
        return false;
    }

    public static SpendCost netOf(SpendCost cost, List<Wallet.PlanetValue> readied, Pooling pooling) {
        int resources = cost.resources();
        int influence = cost.influence();
        List<Wallet.PlanetValue> largestFirst = readied.stream()
                .sorted(Comparator.comparingInt(
                        (Wallet.PlanetValue planet) -> -Math.max(planet.resources(), planet.influence())))
                .toList();
        for (Wallet.PlanetValue planet : largestFirst) {
            if (pooling.paysBothKinds()) {
                resources = Math.max(0, resources - planet.resources());
                influence = Math.max(0, influence - planet.influence());
                continue;
            }
            boolean forResources = resources > 0 && (influence == 0 || planet.resources() >= planet.influence());
            if (forResources) {
                resources = Math.max(0, resources - planet.resources());
            } else if (influence > 0) {
                influence = Math.max(0, influence - planet.influence());
            }
        }
        return new SpendCost(resources, influence, cost.tradeGoods(), cost.tokens());
    }
}
