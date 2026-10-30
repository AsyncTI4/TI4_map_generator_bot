package ti4.ai.hacan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.brain.StrategyCard;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.strategy.CardPlanMemory;
import ti4.ai.strategy.StrategyCardRanking;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class DatahubRules {

    public static final String START = "startQDN";
    public static final String SELECT_PREFIX = "selectBeforeSwapSCs_";
    public static final String SWAP_PREFIX = "swapSCs_";
    private static final String DATAHUB = "qdn";
    private static final String DATAHUB_TEXT = "Quantum Datahub Node";
    private static final String DECLINE = "deleteButtons";
    private static final String KEY = "datahub|";
    private static final String DECLINED = "declined";
    private static final String DONE = "done";
    private static final String FIELD = "~";
    private static final int TRADE_GOODS = 3;
    private static final double TRADE_GOOD_VALUE = 1.0;
    private static final double STRATEGY_TOKEN_VALUE = 1.0;
    private static final double LOST_FOLLOW_VALUE = 1.5;
    private static final double RIVAL_SHARE = 0.5;
    private static final double MIN_GAIN = 0.5;

    record Swap(String faction, int theirs, int mine, double gain) {

        String encode() {
            return String.join(FIELD, faction, String.valueOf(theirs), String.valueOf(mine));
        }

        static Optional<Swap> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null || fields.length != 3) return Optional.empty();
            if (!StringUtils.isNumeric(fields[1]) || !StringUtils.isNumeric(fields[2])) return Optional.empty();
            return Optional.of(new Swap(fields[0], Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), 0));
        }

        String swapHandler() {
            return SWAP_PREFIX + faction + "_" + DATAHUB + "_" + theirs + "_" + mine;
        }

        String selectHandler() {
            return SELECT_PREFIX + faction + "_" + DATAHUB;
        }
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        Player seat = context.seat();
        if (!seat.hasTech(DATAHUB)) return Optional.empty();
        String key = KEY + context.game().getRound();
        String state = context.memory().get(key).orElse("");
        List<AiPrompt> visible = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden())
                .toList();
        if (state.isEmpty()) return offer(context, visible, key);
        Optional<Swap> planned = Swap.decode(state);
        if (planned.isEmpty()) return Optional.empty();
        Optional<Match> swap = Prompts.unowned(visible, planned.get().swapHandler()::equals);
        if (swap.isPresent()) {
            context.memory().put(key, DONE);
            return Optional.of(swap.get().press("swap strategy cards with Quantum Datahub Node"));
        }
        return Prompts.unowned(visible, planned.get().selectHandler()::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()))
                .map(match -> match.press("choose whose strategy card to take with Quantum Datahub Node"));
    }

    private static Optional<AiDecision> offer(AiTurnContext context, List<AiPrompt> visible, String key) {
        for (AiPrompt prompt : visible) {
            if (!prompt.content().contains(DATAHUB_TEXT) || !Prompts.mentions(prompt, context.seat())) continue;
            Optional<PromptButton> start =
                    prompt.firstEnabled(button -> button.isUnowned() && START.equals(button.handlerId()));
            if (start.isEmpty()) continue;
            Optional<Swap> best = bestSwap(context.game(), context.seat());
            if (best.isPresent()) {
                context.memory().put(key, best.get().encode());
                CardPlanMemory.write(
                        context,
                        StrategyCardRanking.pickPlan(
                                context.game(), context.seat(), best.get().theirs()));
                return Optional.of(AiDecision.press(
                        prompt,
                        start.get(),
                        "take " + best.get().faction() + "'s strategy card with Quantum Datahub Node"));
            }
            Optional<PromptButton> decline =
                    prompt.firstEnabled(button -> button.isUnowned() && DECLINE.equals(button.handlerId()));
            if (decline.isEmpty()) continue;
            context.memory().put(key, DECLINED);
            return Optional.of(AiDecision.press(prompt, decline.get(), "keep its strategy card"));
        }
        return Optional.empty();
    }

    static Optional<Swap> bestSwap(Game game, Player seat) {
        if (!affordable(game, seat) || seat.getSCs().isEmpty()) return Optional.empty();
        double cost = TRADE_GOODS * TRADE_GOOD_VALUE + tokenCost(game, seat);
        List<Swap> swaps = new ArrayList<>();
        for (Player other : game.getRealPlayers()) {
            if (other == seat || other.getSCs().isEmpty()) continue;
            for (int theirs : other.getSCs()) {
                for (int mine : offeredOwnCards(seat, other)) {
                    if (helpsLeaderScore(game, seat, other, theirs, mine)) continue;
                    double gain = gain(game, seat, other, theirs, mine) - cost;
                    swaps.add(new Swap(other.getFaction(), theirs, mine, gain));
                }
            }
        }
        return swaps.stream().filter(swap -> swap.gain() > MIN_GAIN).max(Comparator.comparingDouble(Swap::gain));
    }

    private static List<Integer> offeredOwnCards(Player seat, Player other) {
        if (other.getSCs().size() > 1) return List.copyOf(seat.getSCs());
        return List.of(seat.getSCs().iterator().next());
    }

    private static double gain(Game game, Player seat, Player other, int theirs, int mine) {
        double ours = value(game, seat, theirs) - value(game, seat, mine);
        double rival = value(game, other, mine) - value(game, other, theirs) + TRADE_GOODS * TRADE_GOOD_VALUE;
        return ours - RIVAL_SHARE * rival;
    }

    static boolean helpsLeaderScore(Game game, Player seat, Player other, int theirs, int mine) {
        if (!Beneficiary.isPointsLeader(game, seat, other)) return false;
        return StrategyCard.of(game, mine) == StrategyCard.IMPERIAL
                || value(game, other, mine) > value(game, other, theirs);
    }

    private static double value(Game game, Player player, int initiative) {
        return StrategyCardRanking.baseValue(game, player, initiative);
    }

    private static boolean affordable(Game game, Player seat) {
        if (seat.getTg() < TRADE_GOODS || seat.getStrategicCC() < 1) return false;
        int reservedTokens = ScoringReserve.of(game, seat).tokens();
        if (seat.getStrategicCC() + seat.getTacticalCC() - 1 < reservedTokens) return false;
        return ScoringReserve.planAfterReserve(game, seat, SpendCost.tradeGoods(TRADE_GOODS))
                .isPresent();
    }

    private static double tokenCost(Game game, Player seat) {
        boolean losesFollow = seat.getStrategicCC() - 1 < StrategyCardRules.strategyTokensWantedForFollows(game, seat);
        return STRATEGY_TOKEN_VALUE + (losesFollow ? LOST_FOLLOW_VALUE : 0);
    }
}
