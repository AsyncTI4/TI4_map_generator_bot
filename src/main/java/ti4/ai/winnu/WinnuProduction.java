package ti4.ai.winnu;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;

@UtilityClass
public class WinnuProduction {

    public static final String AGENT = "winnuagent";
    public static final int AGENT_DISCOUNT = 2;
    static final String HEGEMONIC_TRADE_POLICY = "htp";
    static final String AGENT_BUTTON = "exhaustAgent_" + AGENT;
    static final String HTP_BUTTON = "exhaustTech_" + HEGEMONIC_TRADE_POLICY;
    static final int MIN_SWAP_GAIN = 2;
    private static final String SPEND_PREFIX = "spend_";
    private static final String RESOURCE_SUFFIX = "_res";

    record Swap(PromptButton button, Planet planet) {

        int gain() {
            return planet.getInfluence() - planet.getResources();
        }
    }

    public static int plannedDiscount(Player seat) {
        return seat.hasUnexhaustedLeader(AGENT) ? AGENT_DISCOUNT : 0;
    }

    public static Optional<AiDecision> discount(
            AiTurnContext context, AiPrompt payment, double owed, SpendCost reserve) {
        Player seat = context.seat();
        if (owed >= AGENT_DISCOUNT
                && plannedDiscount(seat) > 0
                && !seat.getSpentThingsThisWindow().contains(AGENT)) {
            Optional<PromptButton> agent = payment.firstEnabled(
                    button -> button.isOwnedBy(context.faction()) && AGENT_BUTTON.equals(button.handlerId()));
            if (agent.isPresent()) {
                return Optional.of(AiDecision.press(
                        payment, agent.get(), "exhaust Berekar Berekon to reduce the cost of the units by 2"));
            }
        }
        return hegemonicTradePolicy(context, payment, owed, reserve);
    }

    private static Optional<AiDecision> hegemonicTradePolicy(
            AiTurnContext context, AiPrompt payment, double owed, SpendCost reserve) {
        Game game = context.game();
        Player seat = context.seat();
        if (owed <= 0 || swappedGain(game, seat) > 0) return Optional.empty();
        Optional<Swap> swap = bestSwap(game, seat, payment, reserve);
        if (swap.isEmpty()) return Optional.empty();
        String planet = swap.get().planet().getName();
        if (seat.getSpentThingsThisWindow().contains(HEGEMONIC_TRADE_POLICY)) {
            return Optional.of(AiDecision.press(
                    payment,
                    swap.get().button(),
                    "spend " + planet + " for its influence with Hegemonic Trade Policy"));
        }
        if (!seat.hasTechReady(HEGEMONIC_TRADE_POLICY)
                || owed < swap.get().planet().getResources() + MIN_SWAP_GAIN) {
            return Optional.empty();
        }
        return payment.enabledHandler(HTP_BUTTON)
                .map(button -> AiDecision.press(
                        payment, button, "exhaust Hegemonic Trade Policy to spend " + planet + " for its influence"));
    }

    static Optional<Swap> bestSwap(Game game, Player seat, AiPrompt payment, SpendCost reserve) {
        Wallet wallet = Wallet.of(game, seat);
        return payment.enabledButtons().stream()
                .filter(button -> button.handlerId().startsWith(SPEND_PREFIX)
                        && button.handlerId().endsWith(RESOURCE_SUFFIX))
                .map(button -> swapOf(game, seat, button))
                .flatMap(Optional::stream)
                .filter(swap -> swap.gain() >= MIN_SWAP_GAIN)
                .filter(swap ->
                        wallet.without(exhausting(swap.planet().getName())).canPay(reserve))
                .max(Comparator.comparingInt(Swap::gain)
                        .thenComparing(swap -> swap.planet().getName(), Comparator.reverseOrder()));
    }

    private static Optional<Swap> swapOf(Game game, Player seat, PromptButton button) {
        String name = StringUtils.removeEnd(StringUtils.removeStart(button.handlerId(), SPEND_PREFIX), RESOURCE_SUFFIX);
        if (!seat.getPlanets().contains(name) || seat.getExhaustedPlanets().contains(name)) return Optional.empty();
        Planet planet = game.getPlanetsInfo().get(name);
        return planet == null ? Optional.empty() : Optional.of(new Swap(button, planet));
    }

    public static int swappedGain(Game game, Player seat) {
        List<String> spent = seat.getSpentThingsThisWindow();
        if (!spent.contains(HEGEMONIC_TRADE_POLICY)) return 0;
        return spent.stream()
                .filter(seat.getPlanets()::contains)
                .map(game.getPlanetsInfo()::get)
                .filter(planet -> planet != null)
                .mapToInt(planet -> planet.getInfluence() - planet.getResources())
                .max()
                .stream()
                .map(gain -> Math.max(0, gain))
                .findFirst()
                .orElse(0);
    }

    private static Wallet.Payment exhausting(String planet) {
        return new Wallet.Payment(List.of(planet), List.of(), 0, 0);
    }
}
