package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.GameStats.ActionCardPlay;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

/**
 * Read from public state at every AI decision, per game: the Direct Hit windows (a ship that can be Direct Hit became
 * damaged), by the type of ship that sustained and whether another player then held the card, how many Direct Hits
 * were played (attributed to the latest window) and how many were canceled, and whether each Destroy Their Greatest
 * Ship holder scored it.
 */
final class DirectHitTracker {

    private static final String DIRECT_HIT = "Direct Hit";
    private static final String DESTROY_THEIR_GREATEST_SHIP = "dtgs";
    private static final List<String> COPIES = List.of("dh1", "dh2", "dh3", "dh4");
    private static final String UNATTRIBUTED = "unattributed";

    private final Map<String, Integer> damagedBefore = new HashMap<>();
    private final Map<String, Integer> windowsByTarget = new LinkedHashMap<>();
    private final Map<String, Integer> windowsToAHolderByTarget = new LinkedHashMap<>();
    private final Map<String, Integer> playedByTarget = new LinkedHashMap<>();
    private final Set<String> dtgsHolders = new LinkedHashSet<>();
    private String lastWindowTarget = UNATTRIBUTED;
    private int playsSeen;
    private int canceledSeen;

    synchronized void observe(Game game) {
        observeWindows(game);
        observePlays(game);
        for (Player seat : game.getRealPlayers()) {
            if (seat.getSecretsUnscored().containsKey(DESTROY_THEIR_GREATEST_SHIP)
                    || seat.getSecretsScored().containsKey(DESTROY_THEIR_GREATEST_SHIP)) {
                dtgsHolders.add(seat.getFaction());
            }
        }
    }

    synchronized List<String> lines(Game game) {
        List<String> lines = new ArrayList<>();
        int windows =
                windowsByTarget.values().stream().mapToInt(Integer::intValue).sum();
        if (windows == 0 && playsSeen == 0 && dtgsHolders.isEmpty()) return lines;
        lines.add(String.format(
                "DirectHit %s: windows %d %s, with a holder in reach %s, played %d %s, canceled %d",
                game.getName(),
                windows,
                windowsByTarget,
                windowsToAHolderByTarget,
                playsSeen,
                playedByTarget,
                canceledSeen));
        for (String faction : dtgsHolders) {
            boolean scored = game.getRealPlayers().stream()
                    .anyMatch(seat -> seat.getFaction().equals(faction)
                            && seat.getSecretsScored().containsKey(DESTROY_THEIR_GREATEST_SHIP));
            lines.add(String.format(
                    "DestroyTheirGreatestShip %s %s: scored %s, round %d",
                    game.getName(), faction, scored, game.getRound()));
        }
        return lines;
    }

    private void observeWindows(Game game) {
        for (Tile tile : game.getTileMap().values()) {
            for (Player owner : game.getRealPlayers()) {
                for (UnitType type : BoardView.MOVING_SHIPS) {
                    observeShips(game, tile, owner, type);
                }
            }
        }
    }

    private void observeShips(Game game, Tile tile, Player owner, UnitType type) {
        UnitModel model = BoardView.model(owner, type).orElse(null);
        if (model == null || !model.getCanBeDirectHit()) return;
        int damaged = BoardView.damaged(BoardView.space(tile), owner, type);
        String key = tile.getPosition() + "|" + owner.getFaction() + "|" + type.name();
        int before = damagedBefore.getOrDefault(key, 0);
        damagedBefore.put(key, damaged);
        if (damaged <= before) return;
        String target = type == UnitType.Flagship || type == UnitType.Warsun
                ? "heavy"
                : type.name().toLowerCase();
        windowsByTarget.merge(target, damaged - before, Integer::sum);
        lastWindowTarget = target;
        if (game.getRealPlayers().stream().anyMatch(holder -> holder != owner && holdsDirectHit(holder))) {
            windowsToAHolderByTarget.merge(target, damaged - before, Integer::sum);
        }
    }

    private void observePlays(Game game) {
        List<ActionCardPlay> plays = game.getGameStats().getActionCardPlays().stream()
                .filter(play -> DIRECT_HIT.equals(play.getActionCard()))
                .toList();
        for (int index = playsSeen; index < plays.size(); index++) {
            playedByTarget.merge(lastWindowTarget, 1, Integer::sum);
        }
        playsSeen = plays.size();
        canceledSeen = (int) plays.stream().filter(ActionCardPlay::isCanceled).count();
    }

    private static boolean holdsDirectHit(Player holder) {
        return holder.getActionCards().keySet().stream().anyMatch(COPIES::contains);
    }
}
