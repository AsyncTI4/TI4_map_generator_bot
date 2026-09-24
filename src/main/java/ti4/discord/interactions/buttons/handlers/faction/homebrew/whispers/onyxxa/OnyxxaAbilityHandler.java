package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.onyxxa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperSCs;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.strategycard.PlayStrategyCardService;

@UtilityClass
public class OnyxxaAbilityHandler {

    private static final String SILENT_ACCORD_USED_KEY = "silentAccordUsedRound";
    private static final String PRIMARY_ACCESS_KEY_PREFIX = "scPrimaryAccess_";
    private static final String SILENT_ACCORD = "bapnconc";

    public static boolean trySpendForStrategicFluidity(
            Game game, Player player, ButtonInteractionEvent event, int scNum) {
        if (!player.hasAbility("strategic_fluidity")) return false;
        if (!player.getFollowedSCs().contains(scNum)) return false;
        if (game.getPlayerFromSC(scNum) == null) return false;
        if (!hasPaidFollowToken(game, player, scNum)) {
            ReactionService.addReaction(event, game, player, ButtonHelperSCs.deductCC(game, player, scNum));
            return true;
        }
        if (hasUsedStrategicFluidity(game, player, scNum)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", you have already used **Strategic Fluidity** on **"
                            + game.getSCName(scNum) + "**.");
            return true;
        }
        if (player.getStrategicCC() < 1) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", you have no command tokens in your strategy pool to spend for **Strategic Fluidity**.");
            return true;
        }
        player.setStrategicCC(player.getStrategicCC() - 1);
        ReactionService.addReaction(
                event,
                game,
                player,
                " spent 1 additional command token from strategy pool"
                        + applyStrategicFluidity(event, game, player, scNum));
        return true;
    }

    public static List<Button> getStrategicFluidityPrimaryButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        if (!player.hasAbility("strategic_fluidity")) return buttons;
        for (int sc : game.getSCList()) {
            if (sc <= 0 || Boolean.TRUE.equals(game.getScPlayed().get(sc))) continue;
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "strategicFluidityPrimary_" + sc,
                    "Primary of " + game.getSCName(sc) + " (Strategic Fluidity)",
                    FactionEmojis.onyxxa));
        }
        return buttons;
    }

    @ButtonHandler("strategicFluidityPrimary_")
    public static void resolveStrategicFluidityPrimary(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int scNum = Integer.parseInt(buttonID.replace("strategicFluidityPrimary_", ""));
        if (player.getStrategicCC() < 1) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", you have no command tokens in your strategy pool to spend for **Strategic Fluidity**.");
            return;
        }
        player.setStrategicCC(player.getStrategicCC() - 1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " spent 1 additional command token from their strategy pool to use "
                        + FactionEmojis.onyxxa + " **Strategic Fluidity** and resolve the primary ability of **"
                        + game.getSCName(scNum) + "** instead of its secondary ability.");
        ButtonHelper.deleteMessage(event);
        PlayStrategyCardService.playSC(
                event, scNum, game, game.getMainGameChannel(), player, true, true, "resolved via Strategic Fluidity");
    }

    public static void markFollowTokenPaid(Game game, Player player, int scNum) {
        String key = getFollowTokenPaidKey(game, scNum);
        game.setStoredValue(key, game.getStoredValue(key) + "_" + player.getFaction());
    }

    public static boolean hasPaidFollowToken(Game game, Player player, int scNum) {
        return game.getStoredValue(getFollowTokenPaidKey(game, scNum)).contains("_" + player.getFaction());
    }

    private static String getFollowTokenPaidKey(Game game, int scNum) {
        return "followTokenPaid" + game.getRound() + "_" + scNum;
    }

    public static boolean canUseStrategicFluidity(Game game, Player player, int scNum) {
        return player.hasAbility("strategic_fluidity")
                && hasPaidFollowToken(game, player, scNum)
                && game.getPlayerFromSC(scNum) != null
                && !hasUsedStrategicFluidity(game, player, scNum);
    }

    public static String applyStrategicFluidity(ButtonInteractionEvent event, Game game, Player player, int scNum) {
        String usedKey = getStrategicFluidityUsedKey(game, scNum);
        game.setStoredValue(usedKey, game.getStoredValue(usedKey) + "_" + player.getFaction());
        Player holder = game.getPlayerFromSC(scNum);
        if (holder != null && holder != player) {
            grantPrimaryAccess(game, scNum, holder, player);
        }
        resolveAutomatedPrimary(event, game, player, scNum);
        onPrimaryResolved(game, player, scNum);
        return " to use " + FactionEmojis.onyxxa + " **Strategic Fluidity** and resolve the primary ability of **"
                + game.getSCName(scNum) + "** instead of its secondary ability.";
    }

    private static boolean hasUsedStrategicFluidity(Game game, Player player, int scNum) {
        return game.getStoredValue(getStrategicFluidityUsedKey(game, scNum)).contains("_" + player.getFaction());
    }

    private static String getStrategicFluidityUsedKey(Game game, int scNum) {
        return "strategicFluidityUsed" + game.getRound() + "_" + scNum;
    }

    public static void checkSilentAccord(Game game, Player player, ButtonInteractionEvent event, int scNum) {
        if (!player.getPromissoryNotesInPlayArea().contains(SILENT_ACCORD)) return;
        Player holder = game.getPlayerFromSC(scNum);
        if (holder == null || holder == player || !holder.ownsPromissoryNote(SILENT_ACCORD)) return;
        String roundMarker = player.getFaction() + "_" + game.getRound();
        if (game.getStoredValue(SILENT_ACCORD_USED_KEY).contains(roundMarker)) return;
        game.setStoredValue(SILENT_ACCORD_USED_KEY, game.getStoredValue(SILENT_ACCORD_USED_KEY) + ";" + roundMarker);
        grantPrimaryAccess(game, scNum, holder, player);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " is using _Silent Accord_ and resolves the primary ability of **"
                        + game.getSCName(scNum) + "** instead of its secondary ability, before "
                        + holder.getRepresentationNoPing() + ".");
        resolveAutomatedPrimary(event, game, player, scNum);
    }

    private static void resolveAutomatedPrimary(ButtonInteractionEvent event, Game game, Player player, int scNum) {
        boolean isTrade = game.getStrategyCardModelByInitiative(scNum)
                .map(scModel -> scModel.usesAutomationForSCID("pok5trade"))
                .orElse(false);
        if (isTrade) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " gained 3 " + MiscEmojis.getTGorNomadCoinEmoji(game) + " "
                            + player.gainTG(3) + " from the primary ability of **Trade**.");
            ButtonHelperAgents.resolveArtunoCheck(player, 3);
            ButtonHelperAbilities.pillageCheck(player, game);
        }
    }

    public static void onPrimaryResolved(Game game, Player player, int scNum) {
        OnyxxaTechHandler.offerStrategicInversion(game, player);
        Player holder = game.getPlayerFromSC(scNum);
        if (holder != null && holder != player) {
            OnyxxaLeaderHandler.checkCommanderUnlock(game, player, holder);
        }
    }

    public static boolean isDetachmentCard(Game game, Player player, int scNum) {
        if (!player.hasAbility("detachment")) return false;
        return player.getSCs().stream().mapToInt(Integer::intValue).min().orElse(-1) == scNum;
    }

    public static boolean handleDetachmentOnPlay(Game game, Player player, int scNum) {
        if (!isDetachmentCard(game, player, scNum)) return false;
        sendDetachmentReminder(game, player, scNum);
        return true;
    }

    public static void onStrategyCardPlayed(Game game, Player player, int scNum) {
        if (isDetachmentCard(game, player, scNum)) return;
        onPrimaryResolved(game, player, scNum);
    }

    private static void sendDetachmentReminder(Game game, Player player, int scNum) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", due to **Detachment** you resolve the secondary ability of **"
                        + game.getSCName(scNum) + "** instead of its primary ability. Like any other player, spend a"
                        + " command token from your strategy pool with the \"Spend A Strategy Token\" button to do so.");
    }

    public static String stripHolderPrefixIfGrantedAccess(
            Game game, Message message, Player player, String componentIdWithoutFfcc) {
        if (game == null || message == null || player == null) return null;
        for (int scNum : getStrategyCardsOnMessage(message)) {
            String access = game.getStoredValue(getPrimaryAccessKey(game, scNum));
            if (access.isEmpty()) continue;
            String holderFaction = StringUtils.substringBefore(access, ";");
            String grantedFactions = StringUtils.substringAfter(access, ";");
            if (!componentIdWithoutFfcc.startsWith(holderFaction + "_")) continue;
            if (!("_" + grantedFactions + "_").contains("_" + player.getFaction() + "_")) continue;
            return componentIdWithoutFfcc.substring(holderFaction.length() + 1);
        }
        return null;
    }

    private static Set<Integer> getStrategyCardsOnMessage(Message message) {
        Set<Integer> scNums = new HashSet<>();
        for (Button button : message.getComponentTree().findAll(Button.class)) {
            String id = button.getCustomId();
            if (id == null || !(id.startsWith("sc_follow_") || id.startsWith("sc_no_follow_"))) continue;
            String scNum = StringUtils.substringAfterLast(id, "_");
            if (StringUtils.isNumeric(scNum)) scNums.add(Integer.parseInt(scNum));
        }
        return scNums;
    }

    private static String getPrimaryAccessKey(Game game, int scNum) {
        return PRIMARY_ACCESS_KEY_PREFIX + game.getRound() + "_" + scNum;
    }

    private static void grantPrimaryAccess(Game game, int scNum, Player holder, Player grantee) {
        String key = getPrimaryAccessKey(game, scNum);
        String access = game.getStoredValue(key);
        if (access.isEmpty()) {
            access = holder.getFaction() + ";" + grantee.getFaction();
        } else {
            access = access + "_" + grantee.getFaction();
        }
        game.setStoredValue(key, access);
    }
}
