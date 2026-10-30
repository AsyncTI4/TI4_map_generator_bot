package ti4.ai.tactical;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.eval.ValueScale;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.Pooling;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;

@UtilityClass
class PaymentPlanner {

    private static final String SPEND_PREFIX = "spend_";
    private static final String RESOURCE_SUFFIX = "_res";
    private static final String ONE_TRADE_GOOD = "reduceTG_1_res";
    private static final double RESOURCES_PER_INFLUENCE = ValueScale.INFLUENCE / ValueScale.RESOURCE;

    static Optional<PromptButton> choosePlanet(Game game, AiPrompt payment, double owed) {
        return choosePlanet(game, Pooling.SEPARATE, payment, owed);
    }

    static Optional<PromptButton> choosePlanet(Game game, Player seat, AiPrompt payment, double owed) {
        return choosePlanet(game, Pooling.of(seat), payment, owed);
    }

    private static Optional<PromptButton> choosePlanet(Game game, Pooling pooling, AiPrompt payment, double owed) {
        return choose(game, pooling, offeredPlanets(game, pooling, payment), owed);
    }

    static Optional<PromptButton> keepingReserve(Game game, Player seat, AiPrompt payment, double owed) {
        return keepingReserve(game, seat, payment, owed, ProductionPlanner.reserve(game, seat));
    }

    static Optional<PromptButton> keepingReserve(
            Game game, Player seat, AiPrompt payment, double owed, SpendCost reserve) {
        Wallet wallet = Wallet.of(game, seat);
        int wanted = (int) Math.ceil(owed);
        Optional<Wallet.Payment> plan = ScoringReserve.planAfterReserve(wallet, reserve, SpendCost.resources(wanted));
        if (plan.isPresent()) {
            Optional<PromptButton> planet = plan.get().forResources().stream()
                    .map(name -> payment.enabledHandler(SPEND_PREFIX + name + RESOURCE_SUFFIX))
                    .flatMap(Optional::stream)
                    .findFirst();
            if (planet.isPresent()) return planet;
            if (plan.get().tradeGoods() > 0) {
                Optional<PromptButton> tradeGood = payment.enabledHandler(ONE_TRADE_GOOD);
                if (tradeGood.isPresent()) return tradeGood;
            }
        }
        List<PromptButton> keepingIt = offeredPlanets(game, wallet.pooling(), payment).stream()
                .filter(button -> wallet.without(exhausting(planetOf(button))).canPay(reserve))
                .toList();
        return choose(game, wallet.pooling(), keepingIt, owed).or(() -> spareTradeGood(wallet, payment, reserve));
    }

    private static Optional<PromptButton> spareTradeGood(Wallet wallet, AiPrompt payment, SpendCost reserve) {
        if (wallet.tradeGoods() <= 0
                || !wallet.withTradeGoods(wallet.tradeGoods() - 1).canPay(reserve)) {
            return Optional.empty();
        }
        return payment.enabledHandler(ONE_TRADE_GOOD);
    }

    private static Wallet.Payment exhausting(String planet) {
        return new Wallet.Payment(List.of(planet), List.of(), 0, 0);
    }

    private static List<PromptButton> offeredPlanets(Game game, Pooling pooling, AiPrompt payment) {
        return payment.enabledButtons().stream()
                .filter(button -> button.handlerId().startsWith(SPEND_PREFIX))
                .filter(button -> button.handlerId().endsWith(RESOURCE_SUFFIX))
                .filter(button -> resources(game, pooling, button) > 0)
                .toList();
    }

    private static Optional<PromptButton> choose(Game game, Pooling pooling, List<PromptButton> planets, double owed) {
        Optional<PromptButton> leastWastefulCovering = planets.stream()
                .filter(button -> resources(game, pooling, button) >= owed)
                .min(Comparator.comparingDouble(button -> wasted(game, pooling, button, owed)));
        if (leastWastefulCovering.isPresent()) return leastWastefulCovering;
        return planets.stream()
                .max(Comparator.comparingInt((PromptButton button) -> resources(game, pooling, button))
                        .thenComparing(Comparator.comparingDouble(
                                        (PromptButton button) -> forgoneInfluence(game, pooling, button))
                                .reversed()));
    }

    private static double wasted(Game game, Pooling pooling, PromptButton button, double owed) {
        return resources(game, pooling, button) - owed + forgoneInfluence(game, pooling, button);
    }

    private static double forgoneInfluence(Game game, Pooling pooling, PromptButton button) {
        if (pooling != Pooling.SEPARATE) return 0;
        Planet planet = game.getPlanetsInfo().get(planetOf(button));
        return planet == null ? 0 : RESOURCES_PER_INFLUENCE * Math.max(0, planet.getInfluence());
    }

    static String describe(PromptButton button) {
        return button.handlerId().startsWith(SPEND_PREFIX) ? planetOf(button) : "a trade good";
    }

    static String planetOf(PromptButton button) {
        return StringUtils.removeEnd(StringUtils.removeStart(button.handlerId(), SPEND_PREFIX), RESOURCE_SUFFIX);
    }

    private static int resources(Game game, Pooling pooling, PromptButton button) {
        Planet planet = game.getPlanetsInfo().get(planetOf(button));
        return planet == null ? 0 : pooling.resourcesOf(planet);
    }
}
