package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TraitRaid;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;

/**
 * Read from public state at every AI decision, per game, for Monopolize Production, Forge an Alliance and Mine Rare
 * Metals: a trait inventory of the map's non-home planets (once), for every holder at each status phase how many planets
 * of the secret's trait it held and how many were free or held by a rival, every secret a seat stopped holding or started
 * holding (discards and redraws, by secret id), and how many secrets each seat scored.
 */
final class TraitTracker {

    private final Map<String, Set<String>> held = new LinkedHashMap<>();
    private final List<String> events = new ArrayList<>();
    private final List<String> holders = new ArrayList<>();
    private boolean inventoried;
    private String inventory = "";

    synchronized void observe(Game game) {
        if (!inventoried && !game.getTileMap().isEmpty()) {
            inventory = inventoryOf(game);
            inventoried = true;
        }
        for (Player seat : game.getRealPlayers()) observeSecrets(game, seat);
    }

    synchronized void record(Game game) {
        for (Player seat : game.getRealPlayers()) {
            for (String secret : ObjectiveValue.TRAIT_SECRETS.keySet()) {
                if (!seat.getSecretsUnscored().containsKey(secret)
                        && !seat.getSecretsScored().containsKey(secret)) continue;
                holders.add(holderLine(game, seat, secret));
            }
        }
    }

    synchronized List<String> lines(Game game) {
        List<String> lines = new ArrayList<>();
        lines.add("TraitInventory " + game.getName() + " " + inventory);
        holders.forEach(line -> lines.add("TraitHolder " + game.getName() + " " + line));
        events.forEach(line -> lines.add("SecretEvent " + game.getName() + " " + line));
        for (Player seat : game.getRealPlayers()) {
            lines.add(String.format(
                    "SecretsScored %s %s: %d %s",
                    game.getName(),
                    seat.getFaction(),
                    seat.getSecretsScored().size(),
                    seat.getSecretsScored().keySet()));
        }
        return lines;
    }

    private void observeSecrets(Game game, Player seat) {
        Set<String> now = new HashSet<>(seat.getSecretsUnscored().keySet());
        now.addAll(seat.getSecretsScored().keySet());
        Set<String> before = held.put(seat.getFaction(), now);
        if (before == null) return;
        for (String secret : before) {
            if (!now.contains(secret)) events.add(event("dropped", game, seat, secret));
        }
        for (String secret : now) {
            if (!before.contains(secret)) events.add(event("drawn", game, seat, secret));
        }
    }

    private static String event(String kind, Game game, Player seat, String secret) {
        return String.format(
                "%s %s %s r%d %s", kind, seat.getFaction(), secret, game.getRound(), game.getPhaseOfGame());
    }

    private static String holderLine(Game game, Player seat, String secret) {
        String trait = ObjectiveValue.TRAIT_SECRETS.get(secret);
        int mine = 0;
        int free = 0;
        int rival = 0;
        for (Tile tile : game.getTileMap().values()) {
            if (tile.isHomeSystem(game)) continue;
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (!countsTrait(game, planet, trait)) continue;
                Player controller = BoardView.controller(game, planet.getName());
                if (controller == seat) mine++;
                else if (controller == null) free++;
                else rival++;
            }
        }
        return String.format(
                "%s %s r%d: %s own %d, free %d, rival %d%s%s",
                seat.getFaction(),
                secret,
                game.getRound(),
                trait,
                mine + ownHomeTrait(game, seat, trait),
                free,
                rival,
                seat.getSecretsScored().containsKey(secret) ? ", scored" : "",
                gates(game, seat));
    }

    private static String gates(Game game, Player seat) {
        String raid = TraitRaid.firstBlockingGate(game, seat);
        String claim = TraitRaid.firstUnclaimedGate(game, seat);
        return (raid.isEmpty() ? "" : ", raidGate " + raid) + (claim.isEmpty() ? "" : ", claimGate " + claim);
    }

    private static int ownHomeTrait(Game game, Player seat, String trait) {
        Tile home = seat.getHomeSystemTile();
        if (home == null) return 0;
        return (int) home.getPlanetUnitHolders().stream()
                .filter(planet -> countsTrait(game, planet, trait))
                .filter(planet -> BoardView.controller(game, planet.getName()) == seat)
                .count();
    }

    private static String inventoryOf(Game game) {
        StringBuilder inventory = new StringBuilder();
        for (String trait : ObjectiveValue.TRAIT_SECRETS.values()) {
            long count = game.getTileMap().values().stream()
                    .filter(tile -> !tile.isHomeSystem(game))
                    .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                    .filter(planet -> countsTrait(game, planet, trait))
                    .count();
            inventory.append(trait).append('=').append(count).append(' ');
        }
        return inventory.toString().trim();
    }

    private static boolean countsTrait(Game game, Planet planet, String trait) {
        return ObjectiveValue.countsForScoring(game, planet)
                && planet.getPlanetTypes().contains(trait);
    }
}
