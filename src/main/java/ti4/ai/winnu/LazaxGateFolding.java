package ti4.ai.winnu;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.tactical.Stall;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;

@UtilityClass
public class LazaxGateFolding {

    public static final String TECH = "lgf";
    static final String INFANTRY = "gf";
    static final double INFANTRY_ON_MECATOL = 1.0;
    private static final String COMPONENT_ACTION = "componentAction";
    private static final String EXHAUST = "exhaustTech_" + TECH;
    private static final String KEY = "lazaxGateFolding|";
    private static final String MENU = "menu";
    private static final String DONE = "done";

    public static Set<String> movementLinks(Game game, Player player, String position) {
        if (player == null || !player.hasTech(TECH) || player.controlsMecatol(false)) return Set.of();
        Tile mecatol = game.getMecatolTile();
        if (mecatol == null || position.equals(mecatol.getPosition())) return Set.of();
        return hasAlphaOrBeta(game, position) ? Set.of(mecatol.getPosition()) : Set.of();
    }

    private static boolean hasAlphaOrBeta(Game game, String position) {
        Tile tile = game.getTileByPosition(position);
        return tile != null && tile.getTileModel() != null && FoWHelper.doesTileHaveAlphaOrBeta(game, position);
    }

    public static Optional<AiDecision> insteadOfTacticalAction(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!mayUse(context)) return Optional.empty();
        double value = INFANTRY_ON_MECATOL + Stall.actionValue(context.game(), context.seat());
        Optional<TacticalPlan> plan = TacticalRules.rememberedPlan(context).or(() -> {
            Optional<TacticalPlan> computed = TacticalPlanner.best(context.game(), context.seat());
            computed.ifPresent(found -> TacticalRules.remember(context, found));
            return computed;
        });
        if (plan.isPresent() && plan.get().score() >= value) return Optional.empty();
        return open(context, thisTurn, "instead of a weak tactical action");
    }

    public static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!mayUse(context)) return Optional.empty();
        return open(context, thisTurn, "instead of passing");
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        if (!MENU.equals(context.memory().get(key(context)).orElse(""))) return Optional.empty();
        Optional<Match> exhaust = Prompts.owned(
                        Prompts.newestFirst(context.prompts()), context.faction(), EXHAUST::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (exhaust.isEmpty()) return Optional.empty();
        context.memory().put(key(context), DONE);
        return Optional.of(exhaust.get().press("exhaust Lazax Gate Folding to place 1 infantry on Mecatol Rex"));
    }

    static boolean mayUse(AiTurnContext context) {
        Player seat = context.seat();
        return seat.hasTechReady(TECH)
                && seat.controlsMecatol(false)
                && Reclamation.inReinforcements(context.game(), seat, INFANTRY)
                && !context.memory().has(key(context));
    }

    private static Optional<AiDecision> open(AiTurnContext context, List<AiPrompt> thisTurn, String when) {
        Optional<Match> menu = Prompts.owned(thisTurn, context.faction(), COMPONENT_ACTION::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (menu.isEmpty()) return Optional.empty();
        context.memory().put(key(context), MENU);
        return Optional.of(menu.get().press("place 1 infantry on Mecatol Rex with Lazax Gate Folding " + when));
    }

    private static String key(AiTurnContext context) {
        return KEY + context.turnKey();
    }
}
