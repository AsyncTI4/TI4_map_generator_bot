package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

/**
 * Read from public state at every AI decision, per game: each home-system planet a seat stops controlling (whose home
 * it is, whether the seat held Become a Martyr at that moment, and whether it had scored it by the end of the game),
 * and the space combats in anomalies that Brave the Void holders fought and won (the holder is judged when the combat
 * starts, the winner when only one side is left in the system).
 */
final class VoidMartyrTracker {

    private static final String BRAVE_THE_VOID = "btv";
    private static final String BECOME_A_MARTYR = "bam";

    private record Loss(String faction, String planet, String homeOf, boolean heldMartyr, int round) {}

    private record Anomaly(String faction, String position, String result) {}

    private final Map<String, Set<String>> homePlanetsBefore = new HashMap<>();
    private final List<Loss> losses = new ArrayList<>();
    private final Set<String> contests = new HashSet<>();
    private final List<Anomaly> anomalies = new ArrayList<>();
    private final Set<String> voidHolders = new LinkedHashSet<>();

    synchronized void observe(Game game) {
        for (Player seat : game.getRealPlayers()) {
            observeHomePlanets(game, seat);
            observeAnomalies(game, seat);
        }
    }

    synchronized List<String> lines(Game game) {
        List<String> lines = new ArrayList<>();
        for (Loss loss : losses) {
            lines.add(String.format(
                    "HomePlanetLost %s %s lost %s (home of %s) in round %d: holds bam %s, scored bam %s",
                    game.getName(),
                    loss.faction(),
                    loss.planet(),
                    loss.homeOf(),
                    loss.round(),
                    loss.heldMartyr(),
                    scoredBy(game, loss.faction(), BECOME_A_MARTYR)));
        }
        for (String faction : voidHolders) {
            long fought = anomalies.stream()
                    .filter(anomaly -> anomaly.faction().equals(faction))
                    .count();
            long won = anomalies.stream()
                    .filter(anomaly -> anomaly.faction().equals(faction) && "won".equals(anomaly.result()))
                    .count();
            lines.add(String.format(
                    "BraveTheVoid %s %s: anomaly combats fought %d, won %d, scored %s",
                    game.getName(), faction, fought, won, scoredBy(game, faction, BRAVE_THE_VOID)));
        }
        return lines;
    }

    private void observeHomePlanets(Game game, Player seat) {
        Set<String> now = new LinkedHashSet<>();
        for (String planet : seat.getPlanets()) {
            Tile tile = game.getTileFromPlanet(planet);
            if (tile != null && tile.isHomeSystem(game)) now.add(planet);
        }
        Set<String> before = homePlanetsBefore.put(seat.getFaction(), now);
        if (before == null) return;
        for (String planet : before) {
            if (now.contains(planet)) continue;
            losses.add(new Loss(
                    seat.getFaction(),
                    planet,
                    ownerOfHome(game, game.getTileFromPlanet(planet)),
                    seat.getSecretsUnscored().containsKey(BECOME_A_MARTYR),
                    game.getRound()));
        }
    }

    private static String ownerOfHome(Game game, Tile tile) {
        return game.getRealAndEliminatedPlayers().stream()
                .filter(player -> player.getHomeSystemTile() == tile)
                .map(Player::getFaction)
                .findFirst()
                .orElse("none");
    }

    private void observeAnomalies(Game game, Player seat) {
        for (Tile tile : game.getTileMap().values()) {
            String key = seat.getFaction() + "|" + tile.getPosition();
            boolean ours = BoardView.hasOwnShips(seat, tile);
            boolean theirs = BoardView.hasEnemyShips(game, seat, tile);
            if (ours && theirs) {
                if (tile.isAnomaly(game, seat) && holdsVoid(seat) && contests.add(key)) {
                    voidHolders.add(seat.getFaction());
                }
                continue;
            }
            if (!contests.remove(key)) continue;
            anomalies.add(
                    new Anomaly(seat.getFaction(), tile.getPosition(), ours ? "won" : theirs ? "lost" : "mutual"));
        }
    }

    private static boolean holdsVoid(Player seat) {
        return seat.getSecretsUnscored().containsKey(BRAVE_THE_VOID)
                || seat.getSecretsScored().containsKey(BRAVE_THE_VOID);
    }

    private static boolean scoredBy(Game game, String faction, String secret) {
        return game.getRealPlayers().stream()
                .anyMatch(seat -> seat.getFaction().equals(faction)
                        && seat.getSecretsScored().containsKey(secret));
    }
}
