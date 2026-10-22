package ti4.ai.sardakk;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.MecatolStake;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatForecast.SpaceOutlook;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class SardakkHeroRules {

    static final double VP = ObjectiveValue.VICTORY_POINT_VALUE;
    static final double MIN_STAKE = VP;
    static final double MIN_LANDING_WIN = 0.65;
    static final double MAX_SPACE_WIN = 0.6;
    static final double HERO_VALUE = 0.5 * VP;
    static final double ENEMY_HOME_VALUE = VP;
    private static final String HERO = "sardakkhero";
    private static final String HERO_BUTTON = "purgeSardakkHero";
    private static final int CUSTODIANS_COST = 6;

    record Case(double spaceWin, double fleetCost, double fightLoss, double landingWin, double stake) {

        boolean worthPlaying() {
            if (stake < MIN_STAKE || landingWin < MIN_LANDING_WIN || spaceWin > MAX_SPACE_WIN) return false;
            double gainedByNotFighting = (1 - spaceWin) * landingWin * stake;
            double extraShipsGivenUp = fleetCost - fightLoss;
            return gainedByNotFighting > extraShipsGivenUp + HERO_VALUE;
        }

        double net() {
            return (1 - spaceWin) * landingWin * stake - (fleetCost - fightLoss) - HERO_VALUE;
        }
    }

    record Target(Planet planet, Case outlook, Map<UnitType, Integer> landing) {}

    public static Optional<AiDecision> next(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!context.isActivePlayer() || !"action".equalsIgnoreCase(game.getPhaseOfGame())) return Optional.empty();
        if (!seat.hasLeaderUnlocked(HERO) || !TacticalRules.inProgress(context)) return Optional.empty();
        Tile tile = game.getTileByPosition(game.getCurrentActiveSystem());
        if (tile == null || tile.getPlanetUnitHolders().isEmpty()) return Optional.empty();
        Optional<Match> button = Prompts.owned(Prompts.thisTurn(context), context.faction(), HERO_BUTTON::equals)
                .filter(found -> !context.alreadyPressed(found.prompt(), found.button()));
        if (button.isEmpty()) return Optional.empty();
        Optional<SpaceOutlook> space = CombatForecast.spaceOutlook(game, seat, tile.getPosition());
        if (space.isEmpty()) return Optional.empty();
        double fleetCost = BoardView.shipCost(BoardView.space(tile), seat);
        Optional<Target> target = bestTarget(context, tile, space.get(), fleetCost);
        if (target.isEmpty() || !target.get().outlook().worthPlaying()) return Optional.empty();
        TacticalRules.remember(context, landingPlan(context, tile, target.get()));
        return Optional.of(button.get()
                .press("skip the space combat with Sh'val and take "
                        + target.get().planet().getName()));
    }

    static Optional<Target> bestTarget(AiTurnContext context, Tile tile, SpaceOutlook space, double fleetCost) {
        Game game = context.game();
        Player seat = context.seat();
        Target best = null;
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (!worthTaking(game, seat, planet)) continue;
            Map<UnitType, Integer> landing = committable(context, tile, planet);
            if (landing.isEmpty()) continue;
            double win = CombatForecast.landingWin(game, seat, tile.getPosition(), planet.getName(), landing);
            Case outlook = new Case(space.win(), fleetCost, space.ourLoss(), win, stake(game, seat, tile, planet));
            if (best == null || outlook.net() > best.outlook().net()) best = new Target(planet, outlook, landing);
        }
        return Optional.ofNullable(best);
    }

    private static boolean worthTaking(Game game, Player seat, Planet planet) {
        return !seat.getPlanets().contains(planet.getName()) || BoardView.enemyGroundForcesOn(game, seat, planet);
    }

    private static Map<UnitType, Integer> committable(AiTurnContext context, Tile tile, Planet target) {
        Player seat = context.seat();
        Map<UnitType, Integer> landing = new EnumMap<>(UnitType.class);
        for (UnitType type : BoardView.GROUND_FORCES) {
            int inSpace = BoardView.count(BoardView.space(tile), seat, type);
            if (inSpace > 0) landing.merge(type, inSpace, Integer::sum);
        }
        if (!BoardView.hasCustodians(target)) {
            CommanderReach.pulls(context.game(), seat, tile, TacticalPlanner.garrison(context.game(), seat))
                    .forEach(pull -> landing.merge(pull.type(), 1, Integer::sum));
        }
        return landing;
    }

    static double stake(Game game, Player seat, Tile tile, Planet planet) {
        double stake = ownObjectiveGain(game, seat, planet) + denial(game, seat, planet);
        if (game.mecatols().contains(planet.getName())) {
            stake += MecatolStake.holdValue(game, seat);
            if (BoardView.hasCustodians(planet) && canPayCustodians(game, seat)) stake += VP;
        }
        if (isAnotherPlayersHome(game, seat, tile)) {
            stake += ENEMY_HOME_VALUE + BoardView.planetValue(planet);
        }
        return stake;
    }

    private static double ownObjectiveGain(Game game, Player seat, Planet planet) {
        ObjectiveValue own = new ObjectiveValue(game, seat);
        return Math.max(0, own.gain(own.before().withPlanet(planet.getName())));
    }

    private static double denial(Game game, Player seat, Planet planet) {
        Player holder = BoardView.controller(game, planet.getName());
        if (holder == null || holder == seat) return 0;
        ObjectiveValue theirs = ObjectiveValue.publicOnly(game, holder);
        double loss = -theirs.gain(theirs.before().withoutPlanet(planet.getName()));
        return Math.clamp(loss, 0, VP);
    }

    private static boolean canPayCustodians(Game game, Player seat) {
        return ScoringReserve.planForCustodians(game, seat).isPresent();
    }

    private static boolean isAnotherPlayersHome(Game game, Player seat, Tile tile) {
        return game.getRealPlayers().stream().anyMatch(other -> other != seat && other.getHomeSystemTile() == tile);
    }

    private static TacticalPlan landingPlan(AiTurnContext context, Tile tile, Target target) {
        int committed =
                target.landing().values().stream().mapToInt(Integer::intValue).sum();
        int present = BoardView.groundForces(target.planet(), context.seat());
        Map<String, Integer> landings = new LinkedHashMap<>();
        landings.put(target.planet().getName(), present + committed);
        return new TacticalPlan(
                Kind.ATTACK,
                tile.getPosition(),
                List.of(),
                landings,
                target.outlook().net());
    }
}
