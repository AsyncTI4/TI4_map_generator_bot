package ti4.discord.interactions.buttons.handlers.actioncards;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Helper;
import ti4.message.GameMessageManager;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionService;

@UtilityClass
class ActionCardButtonHandler {

    @ButtonHandler("no_sabotage")
    public static void noSabotage(ButtonInteractionEvent event, Game game, Player player) {
        String message = game.isFowMode() ? "No Sabotage" : null;
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler("increaseTGonSC_")
    public static void increaseTGonSC(ButtonInteractionEvent event, String buttonID, Game game) {
        String sc = buttonID.replace("increaseTGonSC_", "");
        int scNum = Integer.parseInt(sc);
        int newTradeGoodCount = game.addTradeGoodsToStrategyCard(scNum, 1);
        boolean useSingular = newTradeGoodCount == 1;
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Added 1 trade good to **" + Helper.getSCName(scNum, game) + "**. There " + (useSingular ? "is" : "are")
                        + " now " + newTradeGoodCount + " trade good" + (useSingular ? "" : "s") + " on it.");
    }

    @ButtonHandler("sabotage_")
    public static void sabotage(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String typeNNameNTarget = buttonID.replace("sabotage_", "");
        String type = typeNNameNTarget.split("_")[0];
        String acName = typeNNameNTarget.split("_")[1];
        String target = "somebody";
        if (typeNNameNTarget.split("_").length > 2) {
            String faction = typeNNameNTarget.split("_")[2];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            target = p2.getRepresentationUnfogged();
        }
        String message = game.getPing() + ", the action card _" + acName + "_ played by " + target
                + " has been canceled by " + player.getRepresentationUnfogged() + " with ";
        game.getGameStats().incrementActionCardSaboCount(acName);
        GameMessageManager.remove(game.getName(), event.getMessageId());
        boolean sendReact = true;
        if ("empy".equalsIgnoreCase(type)) {
            message +=
                    "a Watcher (Empyrean mech)! The relevant Watcher should now be removed by the owner. Note that the bot offers buttons to remove any mech, but the owner should remove one that was next to the player who played the action card.";
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    "Remove the Watcher",
                    ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "mech", true));
            ButtonHelper.deleteMessage(event);
        } else if ("tf".equalsIgnoreCase(type)) {
            message += " three Triune fighters! The relevant fighters should now be removed by the owner.";
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    "Remove the Fighters",
                    ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "ff", true));

        } else if ("xxcha".equalsIgnoreCase(type)) {
            message += "_Instinct Training_! A command token was removed from strategy pool.";
            if (player.hasTech(AliasHandler.resolveTech("Instinct Training"))) {
                if (game.isTwilightsFallMode()) {
                    message += " (the Twilight's Fall version of _Instinct Training_ does not exhaust).";
                } else {
                    message += " The technology is now exhausted.";
                    player.exhaustTech(AliasHandler.resolveTech("Instinct Training"));
                }
                if (player.getStrategicCC() > 0) {
                    player.setStrategicCC(player.getStrategicCC() - 1);
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event);
                }
                ButtonHelper.deleteMessage(event);
            } else {
                sendReact = false;
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        "You clicked the _Instinct Training_ button but you do not have the "
                                + (game.isTwilightsFallMode() ? "ability" : "technology") + ".");
            }
        } else if ("ac".equalsIgnoreCase(type)) {
            message += "a _Sabotage_!";
            boolean hasSabo = false;
            String saboID = "3";
            for (String AC : player.getActionCards().keySet()) {
                if (AC.contains("sabo") || AC.contains("shatter")) {
                    hasSabo = true;
                    saboID = "" + player.getActionCards().get(AC);
                    break;
                }
            }
            if (player.hasPlanet("garbozia")) {
                for (String AC : ActionCardHelper.getGarboziaActionCards(player.getGame())
                        .keySet()) {
                    if (AC.contains("sabo") || AC.contains("shatter")) {
                        hasSabo = true;
                        saboID = ""
                                + ActionCardHelper.getGarboziaActionCards(player.getGame())
                                        .get(AC);
                        break;
                    }
                }
            }
            if (hasSabo) {
                String error = ActionCardHelper.playAC(event, game, player, saboID, game.getActionsChannel());
                if (error != null) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), error);
                    return;
                }
            } else {
                message = "Tried to play a _Sabotage_ but found none in hand.";
                sendReact = false;
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        player.getRepresentation()
                                + ", you clicked the _Sabotage_ action card button but did not have a _Sabotage_ in hand.");
            }
        }

        if (acName.contains("Rider") || acName.contains("Sanction")) {
            AgendaHelper.reverseRider("reverse_" + acName, game, player);
        }
        if (sendReact) {
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(
                        game.getActionsChannel(),
                        game.getPing() + ", the action card _" + acName + "_ has been canceled.");
            } else {
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), message);
            }
        }
    }
}
