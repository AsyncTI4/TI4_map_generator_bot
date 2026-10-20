package ti4.ai.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.FringeStack;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

// Occupy the Fringe holders, read from public state: one line per holder at each status phase (best stack, whether it
// is in the home system or next to it, how many planets the home system gives), the infantry lifted off a dock planet
// during an activation of its own system (the in-system shift), and at game end the ground forces still parked in a
// protected stack (the side-effect guard for the stack protection).
final class FringeTracker {

    private final Map<String, Integer> shifted = new HashMap<>();
    private final Map<String, Integer> dockInfantry = new HashMap<>();
    private int lastRound;
    private boolean ended;

    void observe(Game game, List<String> lines) {
        trackShifts(game);
        if (statusWindow(game) && game.getRound() != lastRound) {
            lastRound = game.getRound();
            for (Player seat : game.getRealPlayers()) {
                if (FringeStack.held(seat)) lines.add(statusLine(game, seat));
            }
        }
    }

    void gameEnd(Game game, List<String> lines) {
        if (ended) return;
        ended = true;
        for (Player seat : game.getRealPlayers()) {
            if (!FringeStack.held(seat)) continue;
            Optional<Planet> stack =
                    FringeStack.best(game, seat).filter(found -> FringeStack.isProtected(game, seat, found));
            lines.add(String.format(
                    "Occupy the Fringe %s end: protected stack %s, shifted %d infantry",
                    seat.getFaction(),
                    stack.map(found -> found.getName() + " " + BoardView.groundForces(found, seat))
                            .orElse("none"),
                    shifted.getOrDefault(seat.getFaction(), 0)));
        }
    }

    private String statusLine(Game game, Player seat) {
        Optional<Planet> best = FringeStack.best(game, seat);
        Tile home = seat.getHomeSystemTile();
        long homePlanets = home == null
                ? 0
                : home.getPlanetUnitHolders().stream()
                        .filter(planet -> seat.getPlanets().contains(planet.getName()))
                        .count();
        String stack = best.map(found -> found.getName() + " " + BoardView.groundForces(found, seat))
                .orElse("none");
        boolean inHome = best.map(found -> game.getTileFromPlanet(found.getName()) == home)
                .orElse(false);
        boolean nearHome = best.map(found -> FringeStack.nearHome(game, seat, found.getName()))
                .orElse(false);
        return String.format(
                "Occupy the Fringe %s r%d: best stack %s, in home system %s, in or next to home %s, home planets held %d, protected %s, shifted %d",
                seat.getFaction(),
                game.getRound(),
                stack,
                inHome,
                nearHome,
                homePlanets,
                best.filter(found -> FringeStack.isProtected(game, seat, found)).isPresent(),
                shifted.getOrDefault(seat.getFaction(), 0));
    }

    // Units cannot leave the active system, so infantry disappearing from a dock planet there while no enemy is
    // present went into the system's space: the first half of an in-system shift.
    private void trackShifts(Game game) {
        Tile active = game.getTileByPosition(game.getActiveSystem());
        for (Player seat : game.getRealPlayers()) {
            if (!FringeStack.held(seat)) continue;
            for (Tile tile : game.getTileMap().values()) {
                for (Planet planet : tile.getPlanetUnitHolders()) {
                    if (BoardView.count(planet, seat, UnitType.Spacedock) == 0) continue;
                    int now = BoardView.count(planet, seat, UnitType.Infantry);
                    Integer before = dockInfantry.put(seat.getFaction() + "|" + planet.getName(), now);
                    boolean liftedInPlace = before != null
                            && before > now
                            && tile == active
                            && !BoardView.hasEnemyUnits(game, seat, tile);
                    if (liftedInPlace) shifted.merge(seat.getFaction(), before - now, Integer::sum);
                }
            }
        }
    }

    private static boolean statusWindow(Game game) {
        String phase = game.getPhaseOfGame();
        if (phase != null && phase.startsWith("status")) return true;
        return "action".equals(phase)
                && !game.getRealPlayers().isEmpty()
                && game.getRealPlayers().stream().allMatch(Player::isPassed);
    }
}
