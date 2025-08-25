package ti4.migration;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.logging.BotLogger;

@UtilityClass
class MigrationHelper {

    public static boolean replaceTokens(Game game, Map<String, String> replacements) {
        boolean found = false;
        for (Tile t : game.getTileMap().values()) {
            for (UnitHolder uh : t.getUnitHolders().values()) {
                Set<String> oldList = new HashSet<>(uh.getTokenList());
                for (Map.Entry<String, String> entry : replacements.entrySet()) {
                    if (oldList.contains(entry.getKey())) {
                        uh.removeToken(entry.getKey());
                        uh.addToken(entry.getValue());
                        found = true;
                    }
                }
            }
        }
        return found;
    }

    public static boolean replaceStage1s(Game game, List<String> decksToCheck, Map<String, String> replacements) {
        if (!decksToCheck.contains(game.getStage1PublicDeckID())) {
            return false;
        }

        boolean mapNeededMigrating = false;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String toReplace = entry.getKey();
            String replacement = entry.getValue();

            mapNeededMigrating |= replace(game.getPublicObjectives1(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getRevealedPublicObjectives(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getScoredPublicObjectives(), toReplace, replacement);
        }
        return mapNeededMigrating;
    }

    public static boolean replaceActionCards(Game game, List<String> decksToCheck, Map<String, String> replacements) {
        if (!decksToCheck.contains(game.getAcDeckID())) {
            return false;
        }

        boolean mapNeededMigrating = false;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String toReplace = entry.getKey();
            String replacement = entry.getValue();

            mapNeededMigrating |= replace(game.getActionCards(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getDiscardActionCards(), toReplace, replacement);

            for (Player player : game.getRealPlayers()) {
                mapNeededMigrating |= replaceKey(player.getActionCards(), toReplace, replacement);
            }
        }
        return mapNeededMigrating;
    }

    public static boolean replaceAgendaCards(Game game, List<String> decksToCheck, Map<String, String> replacements) {
        if (!decksToCheck.contains(game.getAgendaDeckID())) {
            return false;
        }

        boolean mapNeededMigrating = false;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String toReplace = entry.getKey();
            String replacement = entry.getValue();

            mapNeededMigrating |= replace(game.getAgendas(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getDiscardAgendas(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getSentAgendas(), toReplace, replacement);
        }
        return mapNeededMigrating;
    }

    private static <K, V> boolean replaceKey(Map<K, V> map, K toReplace, K replacement) {
        if (map.containsKey(toReplace)) {
            V value = map.get(toReplace);
            map.put(replacement, value);
            map.remove(toReplace);
            return true;
        }
        return false;
    }

    private static <K> boolean replace(List<K> list, K toReplace, K replacement) {
        int index = list.indexOf(toReplace);
        if (index > -1) {
            list.set(index, replacement);
            return true;
        }
        return false;
    }

    public static void swapBagItem(DraftBag bag, int index, DraftItem newItem) {
        BotLogger.info(String.format(
                "Draft Bag replacing %s with %s", bag.Contents.get(index).getAlias(), newItem.getAlias()));
        bag.Contents.remove(index);
        bag.Contents.add(index, newItem);
    }

    public static boolean setPiFactionsHomebrew(Game game) {
        boolean changed = false;
        for (Player player : game.getPlayers().values()) {
            String faction = player.getFaction();
            if (faction != null && faction.startsWith("pi_")) {
                player.setFaction(faction.substring(3));
                changed = true;
            }
        }
        if (changed) {
            game.setHomebrew(true);
            BotLogger.info("Changed factions from 'pi_' in game: " + game.getName());
        }
        return changed;
    }
}
