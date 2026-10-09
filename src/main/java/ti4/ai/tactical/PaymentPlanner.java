package ti4.ai.tactical;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class PaymentPlanner {

    private static final String SPEND_PREFIX = "spend_";
    private static final String RESOURCE_SUFFIX = "_res";
    private static final String ONE_TRADE_GOOD = "reduceTG_1_res";

    static Optional<PromptButton> choosePlanet(Game game, AiPrompt payment, double owed) {
        List<PromptButton> planets = payment.enabledButtons().stream()
                .filter(button -> button.handlerId().startsWith(SPEND_PREFIX))
                .filter(button -> button.handlerId().endsWith(RESOURCE_SUFFIX))
                .filter(button -> resources(game, button) > 0)
                .toList();
        Optional<PromptButton> smallestCovering = planets.stream()
                .filter(button -> resources(game, button) >= owed)
                .min(Comparator.comparingInt(button -> resources(game, button)));
        if (smallestCovering.isPresent()) return smallestCovering;
        return planets.stream().max(Comparator.comparingInt(button -> resources(game, button)));
    }

    static Optional<PromptButton> keepingReserve(Game game, Player seat, AiPrompt payment, double owed) {
        int wanted = (int) Math.ceil(owed);
        Optional<Wallet.Payment> plan = ScoringReserve.planAfterReserve(game, seat, SpendCost.resources(wanted));
        if (plan.isEmpty()) return Optional.empty();
        Optional<PromptButton> planet = plan.get().forResources().stream()
                .map(name -> payment.enabledHandler(SPEND_PREFIX + name + RESOURCE_SUFFIX))
                .flatMap(Optional::stream)
                .findFirst();
        if (planet.isPresent() || plan.get().tradeGoods() == 0) return planet;
        return payment.enabledHandler(ONE_TRADE_GOOD);
    }

    static String planetOf(PromptButton button) {
        return StringUtils.removeEnd(StringUtils.removeStart(button.handlerId(), SPEND_PREFIX), RESOURCE_SUFFIX);
    }

    private static int resources(Game game, PromptButton button) {
        return BoardView.planetResources(game, planetOf(button));
    }
}
