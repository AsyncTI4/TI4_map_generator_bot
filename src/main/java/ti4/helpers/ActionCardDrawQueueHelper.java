package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.service.game.IsPlayerElectedService;

@UtilityClass
public class ActionCardDrawQueueHelper {
    public static final String STATUS_QUEUE_SUFFIX = "Status";
    public static final String POLITICS_QUEUE_SUFFIX = "Politics";

    public static int getAvailableActionCardCount(Game game) {
        int discardAvailable = (int) game.getDiscardActionCards().keySet().stream()
                .filter(ac -> game.getDiscardACStatus().get(ac) == null)
                .count();
        return game.getActionCards().size() + discardAvailable;
    }

    public static int getStatusPhaseDrawCount(Game game, Player player) {
        int count = 0;
        boolean hasMinisterPolicy = IsPlayerElectedService.isPlayerElected(game, player, "minister_policy");
        if (!ButtonHelper.isLawInPlay(game, "absol_minspolicy")) {
            count += 1;
        }
        if (player.hasTech("nm")) {
            count += 1;
        }
        if (player.hasTech("tf-inheritancesystems")) {
            count += 1;
        }
        if (player.hasAbility("scheming")) {
            count += 1;
        }
        count += getSlumberstateBonus(game, player);
        if (hasMinisterPolicy && !player.hasAbility("scheming")) {
            count += 1;
        }
        return Math.max(0, count);
    }

    public static boolean shouldQueueStatusPhaseDraws(Game game) {
        int totalDraws = 0;
        for (Player player : game.getRealPlayers()) {
            totalDraws += getStatusPhaseDrawCount(game, player);
        }
        return totalDraws > getAvailableActionCardCount(game);
    }

    public static void initializeQueue(Game game, List<Player> order, String suffix) {
        game.setStoredValue(queueKey(suffix), "");
        StringBuilder blockers = new StringBuilder();
        for (Player player : order) {
            blockers.append(player.getFaction()).append("*");
        }
        game.setStoredValue(blockerKey(suffix), blockers.toString());
    }

    public static boolean isQueueActive(Game game, String suffix) {
        return !game.getStoredValue(blockerKey(suffix)).isEmpty();
    }

    public static Player getNextBlockedPlayer(Game game, List<Player> order, String suffix) {
        String blockers = game.getStoredValue(blockerKey(suffix));
        if (blockers.isEmpty()) {
            return null;
        }
        for (Player player : order) {
            if (blockers.contains(player.getFaction() + "*")) {
                return player;
            }
        }
        return null;
    }

    public static boolean isPlayerQueued(Game game, Player player, String suffix) {
        return game.getStoredValue(queueKey(suffix)).contains(player.getFaction() + "*");
    }

    public static void enqueuePlayer(Game game, Player player, String suffix) {
        if (!isPlayerQueued(game, player, suffix)) {
            game.setStoredValue(
                    queueKey(suffix), game.getStoredValue(queueKey(suffix)) + player.getFaction() + "*");
        }
    }

    public static void markPlayerResolved(Game game, Player player, String suffix) {
        String blockers = game.getStoredValue(blockerKey(suffix));
        if (blockers.contains(player.getFaction() + "*")) {
            game.setStoredValue(blockerKey(suffix), blockers.replace(player.getFaction() + "*", ""));
        }
        String queue = game.getStoredValue(queueKey(suffix));
        if (queue.contains(player.getFaction() + "*")) {
            game.setStoredValue(queueKey(suffix), queue.replace(player.getFaction() + "*", ""));
        }
    }

    public static void resolveQueue(Game game, List<Player> order, String suffix, Consumer<Player> drawAction) {
        while (true) {
            Player next = getNextBlockedPlayer(game, order, suffix);
            if (next == null) {
                clearQueue(game, suffix);
                return;
            }
            if (!isPlayerQueued(game, next, suffix)) {
                return;
            }
            drawAction.accept(next);
            markPlayerResolved(game, next, suffix);
        }
    }

    private static void clearQueue(Game game, String suffix) {
        game.setStoredValue(queueKey(suffix), "");
        game.setStoredValue(blockerKey(suffix), "");
    }

    private static String queueKey(String suffix) {
        return "queueToDrawACs" + suffix;
    }

    private static String blockerKey(String suffix) {
        return "potentialACDrawBlockers" + suffix;
    }

    private static int getSlumberstateBonus(Game game, Player player) {
        Player titans = Helper.getPlayerFromUnlockedBreakthrough(game, "titansbt");
        if (titans == null) {
            return 0;
        }
        int slumberBonus = 0;
        var countPlanetForSlumber = (java.util.function.Predicate<Planet>)
                p -> player.getPlanets().contains(p.getName());
        if (player == titans) {
            countPlanetForSlumber = countPlanetForSlumber.negate();
        }
        List<String> colorsCoexisting = game.getTileMap().values().stream()
                .flatMap(t -> t.getPlanetUnitHolders().stream())
                .filter(countPlanetForSlumber)
                .filter(p -> p.getUnitColorsOnHolder().contains(player.getColorID()))
                .flatMap(p -> p.getUnitColorsOnHolder().stream())
                .toList();
        List<String> seenColors = new ArrayList<>();
        for (String col : colorsCoexisting) {
            Player p2 = game.getPlayerFromColorOrFaction(col);
            if (player == p2) {
                continue;
            }
            if (player == titans && !seenColors.contains(col)) {
                slumberBonus++;
                seenColors.add(col);
            } else if (p2 == titans) {
                slumberBonus++;
                break;
            }
        }
        return slumberBonus;
    }
}
