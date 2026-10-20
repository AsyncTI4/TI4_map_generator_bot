package ti4.ai.legendary;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FleetLimit;
import ti4.ai.explore.ExploreValues;
import ti4.ai.perception.AiPrompt;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class AvernusRules {

    private static final String KEY = "avernus|";
    private static final String FIELD = "~";
    private static final String COMPONENT_ACTION = "componentAction";
    public static final String TILE_PREFIX = "starforgeTileFree_";
    private static final String DESTROYER_PREFIX = "starforgeFree_destroyer_";
    private static final String FIGHTERS_PREFIX = "starforgeFree_fighters_";
    private static final int FIGHTERS_PLACED = 2;
    private static final long STEP_WAIT_MILLIS = 45_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    private enum Stage {
        MENU,
        ABILITY,
        TILE,
        UNIT,
        DONE
    }

    private record Flow(Stage stage, long since) {

        String encode() {
            return stage.name() + FIELD + since;
        }

        static Optional<Flow> decode(String encoded) {
            String[] fields = StringUtils.split(encoded, FIELD);
            if (fields == null || fields.length != 2 || !StringUtils.isNumeric(fields[1])) return Optional.empty();
            try {
                return Optional.of(new Flow(Stage.valueOf(fields[0]), Long.parseLong(fields[1])));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    public static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!canStart(context) || stronghold(context.game(), context.seat()).isEmpty()) return Optional.empty();
        Optional<Match> menu = Prompts.owned(thisTurn, context.faction(), COMPONENT_ACTION::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        menu.ifPresent(match -> remember(context, new Flow(Stage.MENU, context.now())));
        return menu.map(match -> match.press("star forge for free with the Nucleus instead of passing"));
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        Optional<Flow> flow = flow(context);
        if (flow.isEmpty() || flow.get().stage() == Stage.DONE) return Optional.empty();
        List<AiPrompt> fresh = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= flow.get().since() - CLOCK_SKEW_MILLIS)
                .toList();
        Optional<AiDecision> step =
                switch (flow.get().stage()) {
                    case MENU -> openAbility(context, fresh);
                    case ABILITY -> chooseSystem(context, fresh);
                    case TILE -> chooseShips(context, fresh);
                    case UNIT, DONE -> Optional.empty();
                };
        if (step.isPresent()) return step;
        long until = flow.get().since() + STEP_WAIT_MILLIS;
        if (context.now() < until) return Optional.of(new AiDecision.Wait(until, "the Nucleus"));
        remember(context, new Flow(Stage.DONE, context.now()));
        return Optional.empty();
    }

    private static boolean canStart(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        return context.isActivePlayer()
                && "action".equalsIgnoreCase(game.getPhaseOfGame())
                && LegendaryPlanets.abilityReady(seat, LegendaryPlanets.AVERNUS)
                && !TacticalRules.inProgress(game, seat)
                && !TacticalRules.actionTaken(context)
                && flow(context).isEmpty();
    }

    private static Optional<Tile> stronghold(Game game, Player seat) {
        return ButtonHelper.getTilesOfPlayersSpecificUnits(game, seat, UnitType.Warsun).stream()
                .filter(tile -> destroyerRoom(game, seat, tile) || fighterRoom(seat, tile))
                .max(Comparator.comparingDouble(tile -> BoardView.shipCost(BoardView.space(tile), seat)));
    }

    private static boolean destroyerRoom(Game game, Player seat, Tile tile) {
        return ExploreValues.hasRoomForUnit(game, seat, UnitType.Destroyer)
                && BoardView.nonFighterShips(BoardView.space(tile), seat) + 1 <= FleetLimit.ships(seat);
    }

    private static boolean fighterRoom(Player seat, Tile tile) {
        return LegendaryOutlook.capacityRoom(seat, tile) >= FIGHTERS_PLACED;
    }

    private static Optional<AiDecision> openAbility(AiTurnContext context, List<AiPrompt> fresh) {
        Optional<Match> ability = Prompts.owned(
                        fresh, context.faction(), LegendaryPlanets.button(LegendaryPlanets.AVERNUS)::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        ability.ifPresent(match -> remember(context, new Flow(Stage.ABILITY, context.now())));
        return ability.map(match -> match.press("use the Nucleus"));
    }

    private static Optional<AiDecision> chooseSystem(AiTurnContext context, List<AiPrompt> fresh) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<Tile> best = stronghold(game, seat);
        Optional<Match> tile = best.flatMap(
                        found -> Prompts.unowned(fresh, (TILE_PREFIX + found.getPosition())::equals))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        tile.ifPresent(match -> remember(context, new Flow(Stage.TILE, context.now())));
        return tile.map(match -> match.press("star forge in the system of its war sun"));
    }

    private static Optional<AiDecision> chooseShips(AiTurnContext context, List<AiPrompt> fresh) {
        Player seat = context.seat();
        Optional<Tile> forge = stronghold(context.game(), seat);
        if (forge.isEmpty()) return Optional.empty();
        String position = forge.get().getPosition();
        boolean destroyer = destroyerRoom(context.game(), seat, forge.get());
        String handler = (destroyer ? DESTROYER_PREFIX : FIGHTERS_PREFIX) + position;
        Optional<Match> ships = Prompts.owned(fresh, context.faction(), handler::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        ships.ifPresent(match -> remember(context, new Flow(Stage.DONE, context.now())));
        return ships.map(match -> match.press(destroyer ? "produce a destroyer for free" : "produce 2 fighters"));
    }

    private static Optional<Flow> flow(AiTurnContext context) {
        return context.memory().get(KEY + context.turnKey()).flatMap(Flow::decode);
    }

    private static void remember(AiTurnContext context, Flow flow) {
        context.memory().put(KEY + context.turnKey(), flow.encode());
    }
}
