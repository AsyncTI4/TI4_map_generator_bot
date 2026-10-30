package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.CannonWipeRules;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;

/**
 * Read from public state at every AI decision, per game: the fights at another player's home system (whether the
 * attacker held Darken the Skies, who won, and whether the secret was scored by the end), the raids each seat made and
 * won or lost there (a side-effect guard), and for Turn Their Fleets to Dust how often a holder had space cannon cover
 * of a lone enemy ship, activated that system and saw the ship destroyed. The bot's own watch is in the seat's memory,
 * so recorded and bailed watches are not visible here; activations and wipes stand in for them.
 */
final class HomeRaidTracker {

    private static final String DARKEN_THE_SKIES = "dts";
    private static final String DUST = "ttfd";

    record Fight(String faction, String position, String holder, boolean heldDarkenTheSkies, String result) {}

    private record Contest(boolean heldDarkenTheSkies) {}

    private record Activation(String faction, String position, String opponent) {}

    private final Map<String, Contest> contests = new HashMap<>();
    private final List<Fight> fights = new ArrayList<>();
    private final Set<String> opportunities = new HashSet<>();
    private final Map<String, Activation> activations = new LinkedHashMap<>();
    private final Set<String> wiped = new HashSet<>();
    private final Set<String> dustHolders = new HashSet<>();

    synchronized void observe(Game game) {
        observeHomeFights(game);
        observeDust(game);
    }

    synchronized List<String> lines(Game game) {
        List<String> lines = new ArrayList<>();
        for (Fight fight : fights) {
            if (!fight.heldDarkenTheSkies()) continue;
            lines.add(String.format(
                    "DarkenTheSkies %s %s at %s (%s): %s, scored %s",
                    game.getName(),
                    fight.faction(),
                    fight.position(),
                    fight.holder(),
                    fight.result(),
                    scoredBy(game, fight.faction(), DARKEN_THE_SKIES)));
        }
        Map<String, int[]> raids = new LinkedHashMap<>();
        for (Fight fight : fights) {
            int[] tally = raids.computeIfAbsent(fight.faction(), faction -> new int[3]);
            tally[0]++;
            if ("won".equals(fight.result())) tally[1]++;
            if ("lost".equals(fight.result())) tally[2]++;
        }
        raids.forEach((faction, tally) -> lines.add(String.format(
                "HomeRaids %s %s: attempted %d, won %d, lost %d",
                game.getName(), faction, tally[0], tally[1], tally[2])));
        for (String faction : dustHolders) {
            long chances = opportunities.stream()
                    .filter(key -> key.contains("|" + faction + "|"))
                    .count();
            long activated = activations.values().stream()
                    .filter(activation -> activation.faction().equals(faction))
                    .count();
            long destroyed = activations.entrySet().stream()
                    .filter(entry -> entry.getValue().faction().equals(faction) && wiped.contains(entry.getKey()))
                    .count();
            lines.add(String.format(
                    "TurnTheirFleetsToDust %s %s: covered a lone ship %d times, activated %d, wiped %d, scored %s",
                    game.getName(), faction, chances, activated, destroyed, scoredBy(game, faction, DUST)));
        }
        return lines;
    }

    private static boolean scoredBy(Game game, String faction, String secret) {
        return game.getRealPlayers().stream()
                .anyMatch(seat -> seat.getFaction().equals(faction)
                        && seat.getSecretsScored().containsKey(secret));
    }

    private void observeHomeFights(Game game) {
        for (Player seat : game.getRealPlayers()) {
            for (Player owner : game.getRealPlayers()) {
                Tile home = owner == seat ? null : owner.getHomeSystemTile();
                if (home == null) continue;
                for (UnitHolder holder : home.getUnitHolders().values()) {
                    observeFight(game, seat, home, holder);
                }
            }
        }
    }

    private void observeFight(Game game, Player seat, Tile home, UnitHolder holder) {
        String key = seat.getFaction() + "|" + home.getPosition() + "|" + holder.getName();
        boolean ours = present(seat, home, holder);
        boolean theirs =
                game.getRealPlayers().stream().anyMatch(other -> other != seat && present(other, home, holder));
        Contest before = contests.get(key);
        if (ours && theirs) {
            if (before == null) contests.put(key, new Contest(holdsDarkenTheSkies(seat)));
            return;
        }
        if (before == null) return;
        contests.remove(key);
        String result = ours ? "won" : theirs ? "lost" : "mutual";
        fights.add(new Fight(
                seat.getFaction(), home.getPosition(), holder.getName(), before.heldDarkenTheSkies(), result));
    }

    private static boolean holdsDarkenTheSkies(Player seat) {
        return seat.getSecretsUnscored().containsKey(DARKEN_THE_SKIES)
                || seat.getSecretsScored().containsKey(DARKEN_THE_SKIES);
    }

    private static boolean present(Player player, Tile tile, UnitHolder holder) {
        if (BoardView.SPACE.equals(holder.getName())) return BoardView.hasOwnShips(player, tile);
        return holder instanceof Planet && BoardView.groundForces(holder, player) > 0;
    }

    private void observeDust(Game game) {
        for (Player seat : game.getRealPlayers()) {
            if (!CannonWipeRules.held(seat)) continue;
            dustHolders.add(seat.getFaction());
            for (Tile tile : game.getTileMap().values()) {
                if (CannonWipeRules.coversAShip(game, seat, tile)) {
                    String key = game.getRound() + "|" + seat.getFaction() + "|" + tile.getPosition();
                    opportunities.add(key);
                    if (seat == game.getActivePlayer() && tile.getPosition().equals(game.getActiveSystem())) {
                        activations.computeIfAbsent(
                                key,
                                ignored -> new Activation(
                                        seat.getFaction(), tile.getPosition(), loneOwner(game, seat, tile)));
                    }
                }
            }
        }
        activations.forEach((key, activation) -> {
            Tile tile = game.getTileByPosition(activation.position());
            Player opponent = game.getPlayerFromColorOrFaction(activation.opponent());
            if (tile != null && opponent != null && BoardView.nonFighterShips(BoardView.space(tile), opponent) == 0) {
                wiped.add(key);
            }
        });
    }

    private static String loneOwner(Game game, Player seat, Tile tile) {
        Player owner = CannonWipeRules.shipOwner(game, seat, tile);
        return owner == null ? "" : owner.getFaction();
    }
}
