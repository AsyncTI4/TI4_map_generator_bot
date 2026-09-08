package ti4.helpers;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kairn.KairnAbilityHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;

public final class ButtonHelperStats {

    @ButtonHandler("convertComms_") // convertComms_12(_stay)
    public static void convertCommButton(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        boolean deleteMsg = !buttonID.endsWith("_stay");
        int amt = Integer.parseInt(buttonID.split("_")[1]);
        Tile tile = null;
        if (deleteMsg && buttonID.split("_").length == 3) {
            tile = game.getTileByPosition(buttonID.split("_")[2]);
        }
        convertComms(event, game, player, amt, deleteMsg, tile);
    }

    @ButtonHandler("gainComms_") // gainComms_12(_stay)
    public static void gainCommsButton(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        boolean deleteMsg = !buttonID.endsWith("_stay");
        int amt = Integer.parseInt(buttonID.split("_")[1]);
        Tile tile = null;
        if (deleteMsg && buttonID.split("_").length == 3) {
            tile = game.getTileByPosition(buttonID.split("_")[2]);
        }
        gainComms(event, game, player, amt, deleteMsg, false, tile);
    }

    public static void convertComms(ButtonInteractionEvent event, Game game, Player player, int amt) {
        convertComms(
                event, game, player, amt, event.getMessage().getContentRaw().contains("explore"));
    }

    public static void convertComms(
            ButtonInteractionEvent event, Game game, Player player, int amt, boolean deleteMsg) {
        convertComms(event, game, player, amt, deleteMsg, null);
    }

    public static void convertComms(
            ButtonInteractionEvent event, Game game, Player player, int amt, boolean deleteMsg, Tile tile) {
        String message, ident = player.getRepresentation();
        if (player.getCommodities() >= amt) {
            player.setCommodities(player.getCommodities() - amt);
            player.setTg(player.getTg() + amt);
            message = "onverted " + amt + " commodit" + (amt == 1 ? "y" : "ies") + " to "
                    + StringHelper.pluralize(amt, "trade good") + ".";
        } else if (player.getCommodities() == 1) {
            message = "onverted their last remaining commodity (less than " + amt + ") into 1 trade good.";
            player.setTg(player.getTg() + player.getCommodities());
            player.setCommodities(0);
        } else {
            message = "onverted their " + player.getCommodities() + " remaining commodities (less than " + amt
                    + ") into " + player.getCommodities() + " trade goods.";
            player.setTg(player.getTg() + player.getCommodities());
            player.setCommodities(0);
        }
        if (tile != null) {
            message += " This is due to a combat that occurred in " + tile.getPosition() + ".";
        }
        if (game.isFowMode()) FoWHelper.pingAllPlayersWithFullStats(game, event, player, "C" + message);

        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), ident + " c" + message);

        if (deleteMsg) ButtonHelper.deleteMessage(event);
    }

    public static void gainComms(
            GenericInteractionCreateEvent event, Game game, Player player, int amt, boolean deleteMsg) {
        gainComms(event, game, player, amt, deleteMsg, false);
    }

    public static void gainComms(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            int amt,
            boolean deleteMsg,
            boolean skipOutput) {
        gainComms(event, game, player, amt, deleteMsg, skipOutput, null);
    }

    public static void gainComms(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            int amt,
            boolean deleteMsg,
            boolean skipOutput,
            Tile tile) {
        String message = player.getRepresentationNoPing();
        String fogMessage;
        int initComm = player.getCommodities();
        if (player.getCommodities() + amt > player.getCommoditiesTotal() && !game.isAgeOfCommerceMode()) {
            player.setCommodities(player.getCommoditiesTotal());
            int gained = player.getCommodities() - initComm;
            message += " gained " + gained + " commodit" + (gained == 1 ? "y" : "ies") + " (" + initComm + "->"
                    + player.getCommoditiesRepresentation() + ").\n-# They would have gained " + amt
                    + " but were limited by their faction's commodity value.";
            fogMessage = "Gained " + gained + " commodit" + (gained == 1 ? "y" : "ies") + " (" + initComm + "->"
                    + player.getCommoditiesRepresentation() + ").";
        } else {
            player.setCommodities(player.getCommodities() + amt);
            message += " gained " + amt + " commodit" + (amt == 1 ? "y" : "ies") + " (" + initComm + "->"
                    + player.getCommoditiesRepresentation() + ").";
            fogMessage = "Gained " + amt + " commodit" + (amt == 1 ? "y" : "ies") + " (" + initComm + "->"
                    + player.getCommoditiesRepresentation() + ").";
        }
        int finalComm = player.getCommodities();
        if (tile != null) {
            message += " This is due to a combat that occurred in " + tile.getPosition() + ".";
        }

        if (!skipOutput) MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        if (game.isFowMode()) FoWHelper.pingAllPlayersWithFullStats(game, event, player, fogMessage);
        ButtonHelperAgents.toldarAgentInitiation(game, player, amt);
        if (deleteMsg) ButtonHelper.deleteMessage(event);
        afterGainCommsChecks(game, player, finalComm - initComm);
    }

    public static void replenishComms(
            GenericInteractionCreateEvent event, Game game, Player player, boolean skipOutput) {
        String message, ident = player.getRepresentationNoPing();
        int initComm = player.getCommodities();
        player.setCommodities(player.getCommodities() + player.getCommoditiesTotal());

        message = "Replenished commodities (" + initComm + "->" + player.getCommodities() + ")";
        int finalComm = player.getCommodities();

        if (!skipOutput) MessageHelper.sendMessageToChannel(player.getCorrectChannel(), ident + " " + message);
        if (game.isFowMode()) FoWHelper.pingAllPlayersWithFullStats(game, event, player, message);

        afterGainCommsChecks(game, player, finalComm - initComm);
        ButtonHelper.resolveMinisterOfCommerceCheck(game, player, event);
        ButtonHelperAgents.cabalAgentInitiation(game, player);
    }

    public static void gainTGs(
            GenericInteractionCreateEvent event, Game game, Player player, int amt, boolean skipOutput) {
        if (amt == 0) return;
        String message = "has gained " + amt + " trade goods " + player.gainTG(amt);
        if (!skipOutput)
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " " + message);
        if (game.isFowMode()) FoWHelper.pingAllPlayersWithFullStats(game, event, player, message);

        // After gain tg checks
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, amt);
    }

    public static void afterGainCommsChecks(Game game, Player player, int realGain) {
        if (player.hasAbility("military_industrial_complex")
                && ButtonHelperAbilities.getBuyableAxisOrders(player, game).size() > 1) {
            String axis = player.getRepresentationUnfogged() + " you have the opportunity to buy _Axis Orders_.";
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), axis, ButtonHelperAbilities.getBuyableAxisOrders(player, game));
        }
        if (realGain > 0
                && player.hasAbility("expeditionary_cache")
                && KairnAbilityHandler.getAvailableExpeditionTokens(game) > 0) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you may place expedition tokens using **Expeditionary Cache**.",
                    KairnAbilityHandler.getExpeditionaryCacheButtons(player, game));
        }
        CommanderUnlockCheckService.checkPlayer(player, "mykomentori");
        Player obsidian = Helper.getPlayerFromAbility(game, "marionettes");
        if (obsidian != null && obsidian.getPuppetedFactionsForPlot("siphon").contains(player.getFaction())) {
            String siphonMsg;
            if (game.isFowMode()) {
                siphonMsg = obsidian.getRepresentation()
                        + ", the puppeted player for _Siphon_ has gained commodities, so you gain " + realGain
                        + " trade goods. ";
            } else {
                siphonMsg = obsidian.getRepresentation()
                        + ", your puppet, " + player.getRepresentationNoPing()
                        + ", has gained commodities, and so you have _Siphon_'d " + realGain
                        + " trade goods. ";
            }
            siphonMsg += "(" + obsidian.getTg() + "->" + (obsidian.getTg() + realGain) + ")";
            MessageHelper.sendMessageToChannel(obsidian.getCorrectChannel(), siphonMsg);
            obsidian.setTg(obsidian.getTg() + realGain);

            ButtonHelperAbilities.pillageCheck(obsidian, game);
        }
    }

    public static void sendGainCCButtons(Game game, Player player, boolean redistribute) {
        List<Button> buttons = null;
        if (redistribute) buttons = ButtonHelper.getGainAndLoseCCButtons(player);
        if (!redistribute) buttons = ButtonHelper.getGainCCButtons(player);
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation()); // redundant

        String message =
                player.getRepresentation() + ", your current command tokens are " + player.getCCRepresentation() + ". ";
        message += "Use the buttons to gain" + (redistribute ? " and redistribute" : "") + " command tokens.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }
}
