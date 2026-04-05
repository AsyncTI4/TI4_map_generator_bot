package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;

public final class DeepgloomService {

    public static void spendOneDebt(Game game, Player player, String ability) {
        String debtPool = "scheming".equals(ability) ? "scheming" : "stall tactics";
        List<Player> bts = game.getPlayersFromBreakthrough("yssarilbt");
        for (Player yss : bts) {
            if (yss.hasStoredValue("enableDeepgloomDebt")) {
                if (player.getDebtTokenCount(yss.getColor(), debtPool) > 0) {
                    player.removeDebtTokens(yss.getColor(), 1, debtPool);
                    int remaining = player.getDebtTokenCount(yss.getColor(), debtPool);

                    // Message to Player
                    String msg = "Removed 1 of " + yss.getRepresentationNoPing();
                    msg += "'s debt from the " + debtPool + " debt pool. ";
                    msg += remaining + " uses remain.";
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);

                    // Message to Yssaril
                    String msg2 = player.getRepresentationNoPing() + " has spent 1 debt to use";
                    msg2 += " your _" + ability + "_ ability. " + remaining + " uses remain.";
                    MessageHelper.sendMessageToChannel(yss.getCardsInfoThread(), msg2);
                    return;
                }
            }
        }
    }

    @ButtonHandler("startYssarilbt")
    private static void startYssarilbt(Game game, Player player) {
        Button enableDisableDebt = player.hasStoredValue("enableDeepgloomDebt")
                ? Buttons.red("yssarilbtToggleDebt", "Disable debt", FactionEmojis.Yssaril)
                : Buttons.green("yssarilbtToggleDebt", "Enable debt", FactionEmojis.Yssaril);
        Button controlIndividually = Buttons.gray("yssarilbtIndividualControl", "Control usage per player");
        Button revokeAll = Buttons.gray("yssarilbtRevokeAll", "Revoke all access and debt tokens");

        List<Button> buttons = List.of(enableDisableDebt, controlIndividually, revokeAll);
        String debtMsg = player.getRepresentationNoPing() + ", you have the option to use special debt tokens";
        debtMsg += " to give other players limited access to either Stall Tactics or Scheming.";
        debtMsg += " Or, you can give individual players *unlimited* access until revoked. You also have the";
        debtMsg += " ability to instantly revoke everyone's access to both Scheming and Stall Tactics immediately.";

        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), debtMsg, buttons);
    }

    @ButtonHandler("yssarilbtToggleDebt")
    private static void yssarilbtToggleDebt(ButtonInteractionEvent event, Game game, Player player) {
        String type = " allowed ";
        if (!player.hasStoredValue("enableDeepgloomDebt")) {
            player.addStoredValue("enableDeepgloomDebt", "y");
            game.setDebtPoolIcon("Scheming", CardEmojis.ActionCard.emojiString());
            game.setDebtPoolIcon("Stall Tactics", CardEmojis.ActionCard.emojiString());
        } else {
            player.removeStoredValue("enableDeepgloomDebt");
            type = " revoked ";
        }

        String msg =
                player.getRepresentationNoPing() + " has" + type + "access to Deepgloom Executable via debt tokens.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("yssarilbtIndividualControl")
    private static void yssarilbtIndividual(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> schemingButtons = getButtonsForAllowRevoke(game, player, "scheming");
        String schemingMsg = player.getRepresentationNoPing() + ", please use these buttons to allow ";
        schemingMsg += "other players to use **Scheming**, or to revoke this allowance.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), schemingMsg, schemingButtons);

        List<Button> stallButtons = getButtonsForAllowRevoke(game, player, "stall_tactics");
        String stallTactMsg = player.getRepresentationNoPing() + ", please use these buttons to allow ";
        stallTactMsg += "other players to use **Stall Tactics**, or to revoke this allowance.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), stallTactMsg, stallButtons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("yssarilbtRevokeAll")
    private static void yssarilbtRevokeAllAccess(ButtonInteractionEvent event, Game game, Player player) {
        List<Player> affected = new ArrayList<>();
        for (Player p2 : game.getPlayers().values()) {
            if (playerHasAccessToAbility(p2, "scheming") || playerHasAccessToAbility(p2, "stall_tactics"))
                affected.add(p2);
        }

        game.removeStoredValue("stalltacticsFactions");
        game.removeStoredValue("schemingFactions");
        player.removeStoredValue("enableDeepgloomDebt");

        String msg = player.getRepresentationNoPing() + " has revoked all access to Deepgloom Executable";
        if (!affected.isEmpty()) {
            msg += ", affecting the following players:\n> ";
            msg += String.join(" ", affected.stream().map(Player::fogSafeEmoji).toList());
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    public static boolean playerHasAccessToAbility(Player player, String ability) {
        if (!"scheming".equals(ability) && !"stall_tactics".equals(ability)) return false;
        return playerAllowedToUseAbilityUnlimited(player, ability)
                || playerAllowedToUseAbilityWithDebt(player, ability);
    }

    private static boolean playerAllowedToUseAbilityWithDebt(Player player, String ability) {
        String debtPool = "scheming".equals(ability) ? "scheming" : "stall tactics";
        List<Player> deepgloomPlayers = player.getGame().getPlayersFromBreakthrough("yssarilbt");
        for (Player yss : deepgloomPlayers) {
            if (yss.hasStoredValue("enableDeepgloomDebt")) {
                if (player.getDebtTokenCount(yss.getColor(), debtPool) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean playerAllowedToUseAbilityUnlimited(Player player, String ability) {
        String factions =
                switch (ability) {
                    case "stall_tactics", "stalltactics" -> player.getGame().getStoredValue("stalltacticsFactions");
                    case "scheming" -> player.getGame().getStoredValue("schemingFactions");
                    default -> "";
                };
        return factions.contains(player.getFaction());
    }

    private static List<Button> getButtonsForAllowRevoke(Game game, Player yssaril, String ability) {
        AbilityModel model =
                switch (ability) {
                    case "scheming" -> Mapper.getAbility("scheming");
                    case "stall_tactics", "stalltactics" -> Mapper.getAbility("stall_tactics");
                    default -> null;
                };
        if (model == null) return List.of();

        String abilityPart = "scheming".equals(ability) ? "scheming" : "stalltactics";

        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayersExcludingThis(yssaril)) {
            String id = "yssarilbtStep2_" + abilityPart + "_" + p2.getFaction();
            String name = p2.getFogSafeDisplayName();
            if (playerAllowedToUseAbilityUnlimited(p2, ability)) {
                buttons.add(Buttons.red(id, "Revoke " + model.getName() + " For " + name, p2.fogSafeEmoji()));
            } else {
                buttons.add(Buttons.green(id, "Allow " + model.getName() + " For " + name, p2.fogSafeEmoji()));
            }
        }
        buttons.add(Buttons.gray("deleteButtons", "Done"));
        return buttons;
    }

    @ButtonHandler("yssarilbtStep2_")
    public static void yssarilbtStep2_(ButtonInteractionEvent event, Game game, Player yssaril, String buttonID) {
        String type = buttonID.split("_")[1];
        AbilityModel model =
                switch (type) {
                    case "scheming" -> Mapper.getAbility("scheming");
                    case "stalltactics" -> Mapper.getAbility("stall_tactics");
                    default -> null;
                };
        if (model == null) return;

        String faction = buttonID.split("_")[2];
        String yssarilName = yssaril.getRepresentationNoPing();
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        String p2Name = game.getPlayerFromColorOrFaction(faction).getFogSafeDisplayName();

        String key = type + "Factions";
        String factions = game.getStoredValue(key);

        if (playerAllowedToUseAbilityUnlimited(p2, type)) {
            game.setStoredValue(key, factions.replace(faction + "_", ""));
            String msg = yssarilName + " revoked **" + model.getName() + "** for " + p2Name + ".";
            MessageHelper.sendMessageToChannel(yssaril.getCorrectChannel(), msg);
        } else {
            game.setStoredValue(key, factions + faction + "_");
            String msg = yssarilName + " allowed **" + model.getName() + "** for " + p2Name + ".";
            MessageHelper.sendMessageToChannel(yssaril.getCorrectChannel(), msg);
        }

        List<Button> buttons = getButtonsForAllowRevoke(game, yssaril, type);
        MessageHelper.editMessageButtons(event, buttons);
    }
}
