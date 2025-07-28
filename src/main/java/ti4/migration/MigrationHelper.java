package ti4.migration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.helpers.Units;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.map.persistence.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameService;
import ti4.service.fow.GMService;

@UtilityClass
public class MigrationHelper {

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
        for (String toReplace : replacements.keySet()) {
            String replacement = replacements.get(toReplace);

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
        for (String toReplace : replacements.keySet()) {
            String replacement = replacements.get(toReplace);

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
        for (String toReplace : replacements.keySet()) {
            String replacement = replacements.get(toReplace);

            mapNeededMigrating |= replace(game.getAgendas(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getDiscardAgendas(), toReplace, replacement);
            mapNeededMigrating |= replaceKey(game.getSentAgendas(), toReplace, replacement);
        }
        return mapNeededMigrating;
    }

    public static <K, V> boolean replaceKey(Map<K, V> map, K toReplace, K replacement) {
        if (map.containsKey(toReplace)) {
            V value = map.get(toReplace);
            map.put(replacement, value);
            map.remove(toReplace);
            return true;
        }
        return false;
    }

    public static <K> boolean replace(List<K> list, K toReplace, K replacement) {
        int index = list.indexOf(toReplace);
        if (index > -1) {
            list.set(index, replacement);
            return true;
        }
        return false;
    }

    public static void swapBagItem(DraftBag bag, int index, DraftItem newItem) {
        BotLogger.info(String.format("Draft Bag replacing %s with %s", bag.Contents.get(index).getAlias(), newItem.getAlias()));
        bag.Contents.remove(index);
        bag.Contents.add(index, newItem);
    }

    public static boolean cleanupFactionEmojis(Game game) {
        if (game.isFrankenGame()) return false;

        boolean anyChanged = false;
        for (Player p : game.getPlayers().values()) {
            String rawEmoji = p.getFactionEmojiRaw();
            if (rawEmoji == null || rawEmoji.equals("null")) continue;

            Emoji e = Emoji.fromFormatted(rawEmoji);
            if (e.getName().equalsIgnoreCase(p.getFaction())) {
                p.setFactionEmoji(null);
                anyChanged = true;
            }
        }
        return anyChanged;
    }

    public static boolean removeWekkersAbsolsPoliticalSecrets(Game game) {
        if ("g14".equals(game.getName())) {
            return false;
        }
        boolean removed = false;
        for (Player player : game.getPlayers().values()) {
            for (String ownedPN : new ArrayList<>(player.getPromissoryNotesOwned())) {
                if (ownedPN.startsWith("wekkerabsol_")) {
                    player.removeOwnedPromissoryNoteByID(ownedPN);
                    removed = true;
                }
            }
        }
        return removed;
    }

    public static boolean removeWekkersAbsolsPoliticalSecretsAgain(Game game) {
        if ("g14".equals(game.getName())) {
            return false;
        }
        boolean removed = false;
        for (Player player : game.getPlayers().values()) {
            for (String pn : new ArrayList<>(player.getPromissoryNotes().keySet())) {
                if (pn.startsWith("wekkerabsol_")) {
                    player.removePromissoryNote(pn);
                    removed = true;
                }
            }
        }
        return removed;
    }

    public static boolean warnGamesWithOldDisplaceMap(Game game) {
        if (game.getMovedUnitsFromCurrentActivation().isEmpty()) return false;
        Player player = game.getActivePlayer();
        if (player == null) return false;

        Map<String, Integer> moved = game.getMovedUnitsFromCurrentActivation();
        for (String unit : moved.keySet()) {
            Integer amt = moved.get(unit);

            // Get the state
            Units.UnitState st = Units.UnitState.none;
            if (unit.contains("damaged")) {
                unit = unit.replace("damaged", "");
                st = Units.UnitState.dmg;
            }

            Units.UnitKey key = Units.getUnitKey(unit, player.getColorID());
            if (key != null) {
                // Add the unit to "unk"
                if (!game.getTacticalActionDisplacement().containsKey("unk"))
                    game.getTacticalActionDisplacement().put("unk", new HashMap<>());
                Map<Units.UnitKey, List<Integer>> uh = game.getTacticalActionDisplacement().get("unk");
                if (!uh.containsKey(key)) uh.put(key, Units.UnitState.emptyList());
                int mv = uh.get(key).get(st.ordinal());
                uh.get(key).set(st.ordinal(), mv + amt);
            }
        }
        game.resetCurrentMovedUnitsFrom1System();
        game.resetCurrentMovedUnitsFrom1TacticalAction();

        String msg = "Hey %s, I redid a lot of the tactical action buttons, and because of this a little bit of information has been lost. ";
        msg += "**__All your units are still accounted for__**, but any units that you moved won't be able to be put back unless you use `undo`. ";
        msg += "Apologies for the inconvenience. Let me know if anything breaks during this tactical action and you need help fixing it.\n\n";
        msg += "Good news though, future tactical actions you'll be able to freely edit your unit movement from each system as much as you like!\n";
        msg += "\\- Jazzxhands";
        String playerMsg = String.format(msg, player.getRepresentationUnfogged());
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), playerMsg);

            String gmMsg = String.format(msg, "GM (on behalf of " + player.getRepresentationUnfoggedNoPing() + ")") + "\n";
            GMService.logActivity(game, gmMsg, true);
        } else if (game.getTableTalkChannel() != null) {
            MessageHelper.sendMessageToChannel(game.getTableTalkChannel(), playerMsg);
        }
        return true;
    }

    public static boolean addBothelperPermissions(Game game) {
        if (game.isHasEnded()) return false;
        if (!game.getStoredValue("addedBothelpers").isEmpty()) return false;
        if (game.isFowMode()) return false;

        game.setStoredValue("addedBothelpers", "Yes");
        GameManager.save(game, "adding bothelper permissions");

        Guild guild = game.getGuild();
        if (guild == null) return false;

        Role bothelperRole = CreateGameService.getRole("Bothelper", guild);
        if (bothelperRole == null) return false;

        long threadPermission = Permission.MANAGE_THREADS.getRawValue();
        List<Member> nonGameBothelpers = new ArrayList<>();
        for (Member botHelper : guild.getMembersWithRoles(bothelperRole)) {
            boolean inGame = false;
            for (Player member : game.getRealPlayers()) {
                if (member.getUserID().equalsIgnoreCase(botHelper.getId())) {
                    inGame = true;
                    break;
                }
            }
            if (!inGame) {
                nonGameBothelpers.add(botHelper);
            }
        }

        TextChannel actionsChannel = game.getMainGameChannel();
        if (actionsChannel != null) {
            for (Member botHelper : nonGameBothelpers) {
                actionsChannel.getManager()
                    .putMemberPermissionOverride(botHelper.getIdLong(), threadPermission, 0)
                    .complete();
            }
        }

        return true;
    }
}