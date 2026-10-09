package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;

public record Wallet(List<PlanetValue> planets, int tradeGoods, int tacticTokens, int strategyTokens) {

    public record PlanetValue(String name, int resources, int influence) {}

    public record Payment(List<String> forResources, List<String> forInfluence, int tradeGoods, int tokens) {

        public int planetCount() {
            return forResources.size() + forInfluence.size();
        }
    }

    private static final int SKIP = 0;
    private static final int AS_RESOURCES = 1;
    private static final int AS_INFLUENCE = 2;
    private static final int UNREACHABLE = Integer.MAX_VALUE;
    private static final int CHOICE_SHIFT = 1_000_000;

    public static Wallet of(Game game, Player seat) {
        Wallet empty = new Wallet(List.of(), seat.getTg(), seat.getTacticalCC(), seat.getStrategicCC());
        return empty.withPlanets(game, seat.getReadiedPlanets());
    }

    public Wallet withPlanets(Game game, Collection<String> names) {
        Map<String, Planet> info = game.getPlanetsInfo();
        List<PlanetValue> combined = new ArrayList<>(planets);
        for (String name : names) {
            Planet planet = info.get(name);
            if (planet == null || holds(combined, name)) continue;
            int resources = Math.max(0, planet.getResources());
            int influence = Math.max(0, planet.getInfluence());
            if (resources + influence > 0) combined.add(new PlanetValue(name, resources, influence));
        }
        return new Wallet(combined, tradeGoods, tacticTokens, strategyTokens);
    }

    public Wallet withTradeGoods(int amount) {
        return new Wallet(planets, Math.max(0, amount), tacticTokens, strategyTokens);
    }

    private static boolean holds(List<PlanetValue> planets, String name) {
        return planets.stream().anyMatch(planet -> planet.name().equals(name));
    }

    public int tokens() {
        return tacticTokens + strategyTokens;
    }

    public int resources() {
        return planets.stream().mapToInt(PlanetValue::resources).sum();
    }

    public int influence() {
        return planets.stream().mapToInt(PlanetValue::influence).sum();
    }

    public Wallet without(Payment payment) {
        List<PlanetValue> left = planets.stream()
                .filter(planet -> !payment.forResources().contains(planet.name()))
                .filter(planet -> !payment.forInfluence().contains(planet.name()))
                .toList();
        int tokensLeft = Math.max(0, tacticTokens + strategyTokens - payment.tokens());
        int strategyLeft = Math.min(strategyTokens, tokensLeft);
        return new Wallet(
                left, Math.max(0, tradeGoods - payment.tradeGoods()), tokensLeft - strategyLeft, strategyLeft);
    }

    public boolean canPay(SpendCost cost) {
        return plan(cost).isPresent();
    }

    public Optional<Payment> plan(SpendCost cost) {
        if (cost.tokens() > tokens() || cost.tradeGoods() > tradeGoods) return Optional.empty();
        int needResources = cost.resources();
        int needInfluence = cost.influence();
        int width = needInfluence + 1;
        int states = (needResources + 1) * width;
        int count = planets.size();
        int[][] best = new int[count + 1][states];
        int[][] choice = new int[count + 1][states];
        for (int[] row : best) Arrays.fill(row, UNREACHABLE);
        best[0][0] = 0;
        for (int i = 0; i < count; i++) {
            PlanetValue planet = planets.get(i);
            for (int state = 0; state < states; state++) {
                if (best[i][state] == UNREACHABLE) continue;
                int resources = state / width;
                int influence = state % width;
                relax(best, choice, i, state, state, best[i][state], SKIP);
                if (planet.resources() > 0 && resources < needResources) {
                    int next = Math.min(needResources, resources + planet.resources()) * width + influence;
                    relax(best, choice, i, state, next, best[i][state] + waste(planet), AS_RESOURCES);
                }
                if (planet.influence() > 0 && influence < needInfluence) {
                    int next = resources * width + Math.min(needInfluence, influence + planet.influence());
                    relax(best, choice, i, state, next, best[i][state] + waste(planet), AS_INFLUENCE);
                }
            }
        }
        int chosen = -1;
        int chosenTradeGoods = UNREACHABLE;
        int chosenWaste = UNREACHABLE;
        for (int state = 0; state < states; state++) {
            if (best[count][state] == UNREACHABLE) continue;
            int tradeGoodsNeeded =
                    cost.tradeGoods() + (needResources - state / width) + (needInfluence - state % width);
            if (tradeGoodsNeeded > tradeGoods) continue;
            if (tradeGoodsNeeded < chosenTradeGoods
                    || (tradeGoodsNeeded == chosenTradeGoods && best[count][state] < chosenWaste)) {
                chosen = state;
                chosenTradeGoods = tradeGoodsNeeded;
                chosenWaste = best[count][state];
            }
        }
        if (chosen < 0) return Optional.empty();
        List<String> forResources = new ArrayList<>();
        List<String> forInfluence = new ArrayList<>();
        int state = chosen;
        for (int i = count; i > 0; i--) {
            int encoded = choice[i][state];
            int how = encoded / CHOICE_SHIFT;
            String planet = planets.get(i - 1).name();
            if (how == AS_RESOURCES) forResources.addFirst(planet);
            if (how == AS_INFLUENCE) forInfluence.addFirst(planet);
            state = encoded % CHOICE_SHIFT;
        }
        return Optional.of(new Payment(forResources, forInfluence, chosenTradeGoods, cost.tokens()));
    }

    private static int waste(PlanetValue planet) {
        return planet.resources() + planet.influence();
    }

    private static void relax(int[][] best, int[][] choice, int i, int from, int to, int cost, int how) {
        if (cost < best[i + 1][to]) {
            best[i + 1][to] = cost;
            choice[i + 1][to] = how * CHOICE_SHIFT + from;
        }
    }
}
