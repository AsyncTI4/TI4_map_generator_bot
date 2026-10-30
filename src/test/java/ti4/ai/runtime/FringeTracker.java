package ti4.ai.runtime;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.FringeStack;
import ti4.ai.tactical.GroundCampaign;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;

// Occupy the Fringe holders, read from public state: one line per holder at each status phase (the anchor stack, the
// infantry built since the previous line, the free transport room near the anchor, the best plan the planner has for
// the anchor's system or why it has none, whether the stack is in the home system or next to it), the infantry lifted
// off a dock planet during an activation of its own system (the in-system shift), and at game end the ground forces
// still parked in a protected stack (the side-effect guard for the stack protection) and whether each holder stood
// on 9 at its last status phase and scored.
final class FringeTracker {

    private final Map<String, Integer> shifted = new HashMap<>();
    private final Map<String, Integer> built = new HashMap<>();
    private final Map<String, Integer> dockInfantry = new HashMap<>();
    private final Map<String, Integer> lastStatusStack = new HashMap<>();
    private final Set<String> holders = new LinkedHashSet<>();
    private int lastRound;
    private boolean ended;

    void observe(Game game, List<String> lines) {
        trackDockInfantry(game);
        if (statusWindow(game) && game.getRound() != lastRound) {
            lastRound = game.getRound();
            for (Player seat : game.getRealPlayers()) {
                if (FringeStack.held(seat)) lines.add(statusLine(game, seat));
                if (seat.getSecretsUnscored().containsKey(GroundCampaign.MECHANIZE_THE_MILITARY)) {
                    lines.add(mechanizeCampaignLine(game, seat));
                }
            }
        }
    }

    void gameEnd(Game game, List<String> lines) {
        if (ended) return;
        ended = true;
        for (Player seat : game.getRealPlayers()) {
            if (!holders.contains(seat.getFaction())) continue;
            Optional<Planet> stack =
                    FringeStack.best(game, seat).filter(found -> FringeStack.isProtected(game, seat, found));
            int atLastStatus = lastStatusStack.getOrDefault(seat.getFaction(), 0);
            lines.add(String.format(
                    "Occupy the Fringe %s end: protected stack %s, shifted %d infantry, stack at last status phase %d (%s), scored %s",
                    seat.getFaction(),
                    stack.map(found -> found.getName() + " " + BoardView.groundForces(found, seat))
                            .orElse("none"),
                    shifted.getOrDefault(seat.getFaction(), 0),
                    atLastStatus,
                    atLastStatus >= FringeStack.GROUND_FORCES ? "reached 9" : "short of 9",
                    seat.getSecretsScored().containsKey(FringeStack.OCCUPY_THE_FRINGE)));
        }
    }

    private String statusLine(Game game, Player seat) {
        String faction = seat.getFaction();
        holders.add(faction);
        Optional<Planet> best = FringeStack.best(game, seat);
        int stack = new ObjectiveValue(game, seat).before().mostGroundForcesWithoutADock();
        lastStatusStack.put(faction, stack);
        Tile home = seat.getHomeSystemTile();
        long homePlanets = home == null
                ? 0
                : home.getPlanetUnitHolders().stream()
                        .filter(planet -> seat.getPlanets().contains(planet.getName()))
                        .count();
        String anchor = best.map(found -> found.getName() + " " + BoardView.groundForces(found, seat))
                .orElse("none");
        boolean inHome = best.map(found -> game.getTileFromPlanet(found.getName()) == home)
                .orElse(false);
        boolean nearHome = best.map(found -> FringeStack.nearHome(game, seat, found.getName()))
                .orElse(false);
        Optional<Tile> anchorTile = best.map(found -> game.getTileFromPlanet(found.getName()));
        int builtSinceLastLine = built.getOrDefault(faction, 0);
        built.remove(faction);
        return String.format(
                        "Occupy the Fringe %s r%d: anchor %s, largest stack %d, infantry built %d, haul room %s, garrison %s, in home system %s, in or next to home %s, home planets held %d, protected %s, shifted %d",
                        faction,
                        game.getRound(),
                        anchor,
                        stack,
                        builtSinceLastLine,
                        anchorTile
                                .map(tile -> String.valueOf(haulRoomNear(game, seat, tile)))
                                .orElse("n/a"),
                        garrisonReport(game, seat, anchorTile),
                        inHome,
                        nearHome,
                        homePlanets,
                        best.filter(found -> FringeStack.isProtected(game, seat, found))
                                .isPresent(),
                        shifted.getOrDefault(faction, 0))
                + campaignReport(game, seat, GroundCampaign.OCCUPY_THE_FRINGE);
    }

    // The campaign behind the line: its anchor and demand, rounds to finish against rounds left, whether it is
    // spending yet, the transport capacity near the docks, and the components that shorten it.
    private static String campaignReport(Game game, Player seat, String secret) {
        GroundCampaign campaign = GroundCampaign.of(game, seat);
        GroundCampaign.Eta eta = campaign.eta(secret);
        boolean fringe = GroundCampaign.OCCUPY_THE_FRINGE.equals(secret);
        return String.format(
                " | campaign demand %d, eta %d of %d rounds left (slack %d, %s), %s, haul capacity %d, Transit Diodes %s, Sling Relay %s, Carrier II %s",
                fringe ? campaign.fringeDemand() : campaign.mechDemand(),
                eta.rounds(),
                eta.roundsLeft(),
                eta.slack(),
                !eta.feasible()
                        ? "infeasible"
                        : (fringe ? campaign.fringeActive() : campaign.mechActive()) ? "active" : "waiting",
                fringe
                        ? "anchor "
                                + campaign.anchor()
                                        .map(planet -> planet.getName())
                                        .orElse("none")
                        : "targets "
                                + campaign.mtmTargets().stream()
                                        .map(planet -> planet.getName())
                                        .toList(),
                haulCapacity(game, seat),
                componentState(seat, "td"),
                componentState(seat, "sr"),
                seat.hasTech("cv2"));
    }

    private static String componentState(Player seat, String tech) {
        if (!seat.hasTech(tech)) return "not owned";
        return seat.hasTechReady(tech) ? "ready" : "used this round";
    }

    private static int haulCapacity(Game game, Player seat) {
        int capacity = 0;
        for (Tile tile : game.getTileMap().values()) {
            UnitHolder space = BoardView.space(tile);
            for (UnitType type : BoardView.MOVING_SHIPS) {
                capacity += BoardView.count(space, seat, type) * BoardView.capacity(seat, type);
            }
        }
        return capacity;
    }

    private String mechanizeCampaignLine(Game game, Player seat) {
        return String.format("Mechanize the Military %s r%d", seat.getFaction(), game.getRound())
                + campaignReport(game, seat, GroundCampaign.MECHANIZE_THE_MILITARY);
    }

    private String garrisonReport(Game game, Player seat, Optional<Tile> anchorTile) {
        if (anchorTile.isEmpty()) return "no anchor";
        Tile tile = anchorTile.get();
        Optional<TacticalPlan> plan = TacticalPlanner.forTarget(game, seat, tile.getPosition());
        if (plan.isPresent())
            return String.format("%s %.1f", plan.get().kind(), plan.get().score());
        if (groundForcesOffTheAnchor(game, seat) == 0) return "none: no ground forces off the anchor";
        if (movableAnywhere(game, seat) == 0) return "none: (a) ground forces exist but none are movable";
        if (haulRoomNear(game, seat, tile) <= 0) {
            return frozenHaulers(game, seat)
                    ? "none: (b) haulers are frozen by a command token"
                    : "none: (b) no haul room near the anchor";
        }
        return "none: (c)/(d) gain at or below zero, or a leave cost (not split)";
    }

    private static int movableAnywhere(Game game, Player seat) {
        return game.getTileMap().values().stream()
                .mapToInt(tile -> TacticalPlanner.movableGroundForces(game, seat, tile))
                .sum();
    }

    private static boolean frozenHaulers(Game game, Player seat) {
        return game.getTileMap().values().stream().anyMatch(tile -> tile.hasPlayerCC(seat) && roomIn(seat, tile) > 0);
    }

    private static int groundForcesOffTheAnchor(Game game, Player seat) {
        Optional<Planet> anchor = FringeStack.best(game, seat);
        int total = 0;
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                boolean isAnchor = anchor.isPresent() && anchor.get().getName().equals(holder.getName());
                if (!isAnchor && !BoardView.SPACE.equals(holder.getName())) {
                    total += BoardView.groundForces(holder, seat);
                }
            }
        }
        return total;
    }

    private static int haulRoomNear(Game game, Player seat, Tile tile) {
        int room = roomIn(seat, tile);
        for (String position : FoWHelper.getAdjacentTiles(game, tile.getPosition(), seat, false)) {
            Tile nearby = game.getTileByPosition(position);
            if (nearby != null && nearby != tile) room += roomIn(seat, nearby);
        }
        return room;
    }

    private static int roomIn(Player seat, Tile tile) {
        UnitHolder space = BoardView.space(tile);
        int capacity = 0;
        for (UnitType type : BoardView.MOVING_SHIPS) {
            capacity += BoardView.count(space, seat, type) * BoardView.capacity(seat, type);
        }
        int carried = BoardView.count(space, seat, UnitType.Fighter) + BoardView.groundForces(space, seat);
        return Math.max(0, capacity - carried);
    }

    // Units cannot leave the active system, so infantry disappearing from a dock planet there while no enemy is
    // present went into the system's space: the first half of an in-system shift. Infantry appearing on a dock planet
    // during the activation of its system were produced (or landed back) there.
    private void trackDockInfantry(Game game) {
        Tile active = game.getTileByPosition(game.getActiveSystem());
        for (Player seat : game.getRealPlayers()) {
            if (!FringeStack.held(seat)) continue;
            for (Tile tile : game.getTileMap().values()) {
                for (Planet planet : tile.getPlanetUnitHolders()) {
                    if (BoardView.count(planet, seat, UnitType.Spacedock) == 0) continue;
                    int now = BoardView.count(planet, seat, UnitType.Infantry);
                    Integer before = dockInfantry.put(seat.getFaction() + "|" + planet.getName(), now);
                    if (before == null || tile != active) continue;
                    boolean liftedInPlace = before > now && !BoardView.hasEnemyUnits(game, seat, tile);
                    if (liftedInPlace) shifted.merge(seat.getFaction(), before - now, Integer::sum);
                    if (now > before) built.merge(seat.getFaction(), now - before, Integer::sum);
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
