package ti4.ai.letnev;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.brain.StrategyCard;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FleetLimit;
import ti4.ai.perception.AiPrompt;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class DarktalonTreillaRules {

    public static final String HERO = "letnevhero";
    static final double MIN_GAIN = 4.0;
    private static final String KEY = "letnevHero|";
    private static final String COMPONENT_ACTION = "componentAction";
    private static final String HERO_BUTTON = "componentActionRes_leader_" + HERO;
    private static final String CHECKED = "checked";
    private static final String FIELD = "~";
    private static final String MENU = "menu";
    private static final String DONE = "done";
    private static final long STEP_WAIT_MILLIS = 60_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    public static boolean ready(Player seat) {
        return seat.hasLeaderUnlocked(HERO) && !FleetLimit.heroLiftsLimit(seat);
    }

    public static Optional<AiDecision> insteadOfTacticalAction(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!canStart(context)) return Optional.empty();
        Optional<Match> menu = Prompts.owned(thisTurn, context.faction(), COMPONENT_ACTION::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (menu.isEmpty()) return Optional.empty();
        context.memory().put(CHECKED + FIELD + context.turnKey(), DONE);
        boolean assault = assaultsMecatol(context.game(), context.seat());
        if (!assault && !wantsToStackMoreThanTheFleetAllows(context.game(), context.seat(), context)) {
            return Optional.empty();
        }
        remember(context, MENU);
        String reason = assault
                ? "play Darktalon Treilla to send its whole fleet at Mecatol Rex"
                : "play Darktalon Treilla to lift its fleet supply for a bigger attack";
        return Optional.of(menu.get().press(reason));
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        Optional<String> stage = current(context);
        if (stage.isEmpty() || DONE.equals(stage.get().split(FIELD)[0])) return Optional.empty();
        String[] fields = stage.get().split(FIELD);
        long since = fields.length > 1 && StringUtils.isNumeric(fields[1]) ? Long.parseLong(fields[1]) : context.now();
        List<AiPrompt> fresh = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= since - CLOCK_SKEW_MILLIS)
                .toList();
        Optional<Match> hero = Prompts.owned(fresh, context.faction(), HERO_BUTTON::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (hero.isPresent()) {
            remember(context, DONE);
            return Optional.of(hero.get().press("play Darktalon Treilla"));
        }
        long until = since + STEP_WAIT_MILLIS;
        if (context.now() < until) return Optional.of(new AiDecision.Wait(until, "Darktalon Treilla"));
        remember(context, DONE);
        return Optional.empty();
    }

    static boolean wantsToStackMoreThanTheFleetAllows(Game game, Player seat, AiTurnContext context) {
        Optional<TacticalPlan> unlimited = FleetLimit.assumingUnlimited(seat, () -> TacticalPlanner.best(game, seat));
        if (unlimited.isEmpty() || unlimited.get().kind() != TacticalPlan.Kind.ATTACK) return false;
        if (!exceedsFleetSupply(game, seat, unlimited.get())) return false;
        double limited = TacticalRules.rememberedPlan(context)
                .or(() -> TacticalPlanner.best(game, seat))
                .map(TacticalPlan::score)
                .orElse(0.0);
        return unlimited.get().score() - Math.max(0, limited) >= MIN_GAIN;
    }

    static boolean assaultsMecatol(Game game, Player seat) {
        Tile mecatol = game.getMecatolTile();
        if (mecatol == null || seat.controlsMecatol(true)) return false;
        boolean imperial = GravleashPlan.holdsUnplayed(game, seat, StrategyCard.IMPERIAL);
        boolean warfare =
                GravleashPlan.hasBreakthrough(seat) && GravleashPlan.holdsUnplayed(game, seat, StrategyCard.WARFARE);
        if (!imperial && !warfare) return false;
        boolean takeable = FleetLimit.assumingUnlimited(seat, () -> TacticalPlanner.ranked(game, seat)).stream()
                .filter(plan -> plan.target().equals(mecatol.getPosition()))
                .anyMatch(plan -> plan.kind() == TacticalPlan.Kind.EXPAND || plan.kind() == TacticalPlan.Kind.ATTACK);
        return takeable && TacticalPlanner.shipsReaching(game, seat, mecatol) > FleetLimit.ships(seat);
    }

    static boolean exceedsFleetSupply(Game game, Player seat, TacticalPlan plan) {
        Tile target = game.getTileByPosition(plan.target());
        if (target == null) return false;
        int moving = plan.moves().stream()
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                .mapToInt(UnitMove::count)
                .sum();
        return BoardView.nonFighterShips(BoardView.space(target), seat) + moving > FleetLimit.ships(seat);
    }

    private static boolean canStart(AiTurnContext context) {
        Player seat = context.seat();
        Game game = context.game();
        return ready(seat)
                && !game.isFowMode()
                && context.isActivePlayer()
                && "action".equalsIgnoreCase(game.getPhaseOfGame())
                && !TacticalRules.inProgress(game, seat)
                && !TacticalRules.actionTaken(context)
                && !context.memory().has(CHECKED + FIELD + context.turnKey())
                && current(context).isEmpty();
    }

    private static Optional<String> current(AiTurnContext context) {
        return context.memory().get(KEY + context.turnKey());
    }

    private static void remember(AiTurnContext context, String stage) {
        context.memory().put(KEY + context.turnKey(), stage + FIELD + context.now());
    }
}
