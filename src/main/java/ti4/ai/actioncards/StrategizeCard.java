package ti4.ai.actioncards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.StrategyCard;
import ti4.ai.perception.AiPrompt;
import ti4.ai.strategy.CardFacade;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class StrategizeCard {

    static final String STRATEGIZE = "strategize1";
    private static final String COPY_PREFIX = "strategize";
    private static final List<String> SECONDARY_BUTTONS = List.of(
            "non_sc_draw_so",
            "diploRefresh2",
            "draw2 AC",
            "construction_spacedock",
            "construction_pds",
            "sc_refresh",
            "warfareBuild",
            "warfareTeBuild");

    static AiCard strategize() {
        return AiActionCards.componentOfCopies(
                STRATEGIZE,
                alias -> alias.startsWith(COPY_PREFIX),
                StrategizeCard::plan,
                "strategize",
                SECONDARY_BUTTONS,
                StrategizeCard::step);
    }

    private record Secondary(StrategyCard card, double value) {}

    static boolean playable(Game game, Player seat) {
        return held(seat) && !game.isFowMode() && ActionCardRules.mayPlayNow(game, seat);
    }

    static boolean secondaryWorthAToken(Game game, Player seat) {
        return playable(game, seat)
                && ranked(game, seat).stream()
                        .findFirst()
                        .filter(best -> best.value() > CardFacade.strategyToken())
                        .isPresent();
    }

    private static boolean held(Player seat) {
        return seat.getActionCards().keySet().stream().anyMatch(alias -> alias.startsWith(COPY_PREFIX));
    }

    static Optional<Plan> plan(Game game, Player seat) {
        if (game.isFowMode() || seat.getStrategicCC() < 1) return Optional.empty();
        List<Secondary> ranked = ranked(game, seat);
        if (ranked.isEmpty()) return Optional.empty();
        Secondary best = ranked.getFirst();
        if (!StrategyCardRules.hasSpareStrategyToken(game, seat, best.card())) return Optional.empty();
        return Optional.of(Plan.aimedAt(
                best.value() - CardFacade.strategyToken(),
                best.card().name(),
                "to perform the secondary of " + best.card().name().toLowerCase() + "."));
    }

    private static List<Secondary> ranked(Game game, Player seat) {
        List<Secondary> secondaries = new ArrayList<>();
        for (int initiative : game.getSCList()) {
            if (initiative <= 0 || game.getPlayedSCs().contains(initiative)) continue;
            if (heldByAnother(game, seat, initiative)) continue;
            StrategyCard card = StrategyCard.of(game, initiative);
            boolean known = secondaries.stream().anyMatch(secondary -> secondary.card() == card);
            if (known) continue;
            StrategyCardRules.secondaryValue(game, seat, card)
                    .ifPresent(value -> secondaries.add(new Secondary(card, value)));
        }
        secondaries.sort(Comparator.comparingDouble(Secondary::value)
                .reversed()
                .thenComparing(secondary -> secondary.card().name()));
        return secondaries;
    }

    private static boolean heldByAnother(Game game, Player seat, int initiative) {
        return game.getRealPlayers().stream()
                .anyMatch(player -> player != seat && player.getSCs().contains(initiative));
    }

    private static Optional<AiDecision> step(AiTurnContext context, String key, CardPlay play) {
        Game game = context.game();
        Player seat = context.seat();
        List<StrategyCard> order = order(game, seat, play.extra());
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden()
                    || prompt.createdAtMillis() < play.playedAt() - CardPrompts.CLOCK_SKEW_MILLIS
                    || !Prompts.mentions(prompt, seat)) {
                continue;
            }
            for (StrategyCard card : order) {
                Optional<AiDecision> decision = StrategyCardRules.startSecondary(context, prompt, card);
                if (decision.isPresent()) {
                    PlayMemory.moveTo(context, key, play, Stage.DONE);
                    return decision;
                }
            }
        }
        return Optional.empty();
    }

    private static List<StrategyCard> order(Game game, Player seat, String planned) {
        List<StrategyCard> order = new ArrayList<>();
        ranked(game, seat).stream()
                .map(Secondary::card)
                .filter(card -> card.name().equals(planned))
                .forEach(order::add);
        ranked(game, seat).stream()
                .map(Secondary::card)
                .filter(card -> !order.contains(card))
                .forEach(order::add);
        return order;
    }
}
