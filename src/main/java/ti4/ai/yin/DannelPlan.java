package ti4.ai.yin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.MecatolStake;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.tactical.CombatForecast;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class DannelPlan {

    static final String HERO = "yinhero";
    static final String UNOWNED = "unowned";
    static final int MAX_INFANTRY = 3;
    static final double VP = ObjectiveValue.VICTORY_POINT_VALUE;
    static final double HERO_VALUE = 0.5 * VP;
    private static final String DMZ = "dmz";
    private static final String MALLICE = "mallice";

    public record Landing(String planet, String owner, int infantry, double value) {}

    public record Plan(List<Landing> landings, double net) {}

    private record Candidate(Planet planet, Tile tile, String owner, double stake) {}

    public static Optional<Plan> best(Game game, Player seat) {
        if (!seat.hasLeaderUnlocked(HERO)) return Optional.empty();
        int budget = Math.min(MAX_INFANTRY, infantryInReinforcements(game, seat));
        if (budget == 0) return Optional.empty();
        double[] value = new double[budget + 1];
        List<List<Landing>> picks = new ArrayList<>();
        for (int i = 0; i <= budget; i++) picks.add(List.of());
        for (Candidate candidate : candidates(game, seat)) {
            List<Landing> options = new ArrayList<>();
            for (int infantry = 1; infantry <= budget; infantry++) {
                double win = CombatForecast.landingWin(
                        game,
                        seat,
                        candidate.tile().getPosition(),
                        candidate.planet().getName(),
                        Map.of(UnitType.Infantry, infantry),
                        false);
                options.add(new Landing(
                        candidate.planet().getName(), candidate.owner(), infantry, win * candidate.stake()));
                if (win >= 1) break;
            }
            for (int room = budget; room >= 1; room--) {
                for (Landing option : options) {
                    if (option.infantry() > room) continue;
                    double total = value[room - option.infantry()] + option.value();
                    if (total <= value[room]) continue;
                    value[room] = total;
                    List<Landing> chosen = new ArrayList<>(picks.get(room - option.infantry()));
                    chosen.add(option);
                    picks.set(room, chosen);
                }
            }
        }
        if (picks.get(budget).isEmpty()) return Optional.empty();
        return Optional.of(new Plan(picks.get(budget), value[budget] - holdValue(game)));
    }

    static double holdValue(Game game) {
        return GameHorizon.roundsLeft(game) > 1 ? HERO_VALUE : 0;
    }

    static int infantryInReinforcements(Game game, Player seat) {
        int cap = seat.getUnitCap(UnitType.Infantry.getValue());
        if (cap <= 0) return MAX_INFANTRY;
        return Math.max(0, cap - ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, UnitType.Infantry.getValue()));
    }

    private static List<Candidate> candidates(Game game, Player seat) {
        ObjectiveValue own = new ObjectiveValue(game, seat);
        Map<Player, ObjectiveValue> rivals = new HashMap<>();
        boolean custodiansPayable = ScoringReserve.planForCustodians(game, seat).isPresent();
        List<Candidate> candidates = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.isHomeSystem(game)) continue;
            for (Planet planet : tile.getPlanetUnitHolders()) {
                String name = planet.getName();
                if (!invadable(game, seat, planet)) continue;
                if (BoardView.hasCustodians(planet) && !custodiansPayable) continue;
                Optional<Player> holder = holder(game, name);
                if (holder.filter(found -> found == seat).isPresent()) continue;
                double stake = BoardView.planetValue(planet)
                        + Math.max(0, own.gain(own.before().withPlanet(name)))
                        + holder.map(found -> denial(game, rivals, found, name)).orElse(0.0)
                        + mecatol(game, seat, planet);
                String owner = holder.map(Player::getFaction).orElse(UNOWNED);
                candidates.add(new Candidate(planet, tile, owner, stake));
            }
        }
        return candidates;
    }

    private static boolean invadable(Game game, Player seat, Planet planet) {
        if (planet.isSpaceStation(game) || planet.getName().toLowerCase().contains(MALLICE)) return false;
        if (planet.getTokenList().stream().anyMatch(token -> token.toLowerCase().contains(DMZ))) return false;
        return BoardView.enemyGroundForcesOn(game, seat, planet) || !BoardView.enemyStructuresOn(game, seat, planet);
    }

    private static Optional<Player> holder(Game game, String planet) {
        return game.getRealPlayersNNeutral().stream()
                .filter(player -> player.getPlanets().contains(planet))
                .findFirst();
    }

    private static double denial(Game game, Map<Player, ObjectiveValue> rivals, Player holder, String planet) {
        if (holder.isNeutral()) return 0;
        ObjectiveValue theirs = rivals.computeIfAbsent(holder, found -> ObjectiveValue.publicOnly(game, found));
        return Math.clamp(-theirs.gain(theirs.before().withoutPlanet(planet)), 0, VP);
    }

    private static double mecatol(Game game, Player seat, Planet planet) {
        if (!game.mecatols().contains(planet.getName())) return 0;
        return MecatolStake.holdValue(game, seat) + (BoardView.hasCustodians(planet) ? VP : 0);
    }
}
