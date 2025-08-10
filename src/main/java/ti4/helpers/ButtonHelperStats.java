package ti4.helpers;

import java.util.List;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.regex.RegexService;

public class ButtonHelperStats {

    static final Pattern convertCommsRegex =
            Pattern.compile("convertComms_" + RegexHelper.intRegex("amt") + "(_stay)?");

    @ButtonHandler("convertComms_") // convertComms_12(_stay)
    public static void convertCommButton(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        boolean deleteMsg = !buttonID.endsWith("_stay");
        RegexService.runMatcher(convertCommsRegex, buttonID, matcher -> {
            int amt = Integer.parseInt(matcher.group("amt"));
            convertComms(event, game, player, amt, deleteMsg);
        });
    }

    static final Pattern gainCommsRegex = Pattern.compile("gainComms_" + RegexHelper.intRegex("amt") + "(_stay)?");

    @ButtonHandler("gainComms_") // gainComms_12(_stay)
    public static void gainCommsButton(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        boolean deleteMsg = !buttonID.endsWith("_stay");
        RegexService.runMatcher(gainCommsRegex, buttonID, matcher -> {
            int amt = Integer.parseInt(matcher.group("amt"));
            gainComms(event, game, player, amt, deleteMsg);
        });
    }

    public static void convertComms(ButtonInteractionEvent event, Game game, Player player, int amt) {
        convertComms(
                event, game, player, amt, event.getMessage().getContentRaw().contains("explore"));
    }

    public static void convertComms(
            ButtonInteractionEvent event, Game game, Player player, int amt, boolean deleteMsg) {
        String message, ident = player.getRepresentation();
        if (player.getCommodities() >= amt) {
            player.setCommodities(player.getCommodities() - amt);
            player.setTg(player.getTg() + amt);
            message = "Converted " + amt + " commodit" + (amt == 1 ? "y" : "ies") + " to " + amt + " trade good"
                    + (amt == 1 ? "" : "s") + ".";
        } else if (player.getCommodities() == 1) {
            message = "Converted their last remaining commodity (less than " + amt + ") into 1 trade good.";
            player.setTg(player.getTg() + player.getCommodities());
            player.setCommodities(0);
        } else {
            message = "Converted their " + player.getCommodities() + " remaining commodities (less than " + amt
                    + ") into " + player.getCommodities() + " trade goods.";
            player.setTg(player.getTg() + player.getCommodities());
            player.setCommodities(0);
        }
        if (game.isFowMode()) FoWHelper.pingAllPlayersWithFullStats(game, event, player, message);

        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), ident + " " + message);

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
        String message = player.getRepresentationNoPing();
        String fogMessage;
        int initComm = player.getCommodities();
        if (player.getCommodities() + amt > player.getCommoditiesTotal()) {
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
        CommanderUnlockCheckService.checkPlayer(player, "mykomentori");
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
