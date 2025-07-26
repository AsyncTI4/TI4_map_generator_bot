package ti4.buttons.handlers.other;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionService;
import ti4.service.leader.CommanderUnlockCheckService;

import java.util.List;

@UtilityClass
class ResourceButtonHandler {

    @ButtonHandler("spend_")
    public static void spend(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planetName = buttonID.replace("spend_", "");
        String message = Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " was exhausted.";

        if (player.exhaustPlanet(planetName)) {
            ReactionService.addReaction(event, game, player, message);
        } else {
            message = "Planet " + planetName + " not found or already exhausted.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("reduceComm_")
    public static void reduceComm(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        int commodities = Integer.parseInt(buttonID.split("_")[1]);
        if (player.getCommodities() >= commodities) {
            player.setCommodities(player.getCommodities() - commodities);
            String message = player.getFactionEmoji() + " reduced commodities by " + commodities +
                " (commodities: " + (player.getCommodities() + commodities) + "->" + player.getCommodities() + ").";
            ReactionService.addReaction(event, game, player, message);
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "You don't have enough commodities. You have " + player.getCommodities() + " commodities.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("reduceTG_")
    public static void reduceTG(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        int tg = Integer.parseInt(buttonID.split("_")[1]);
        if (player.getTg() >= tg) {
            player.setTg(player.getTg() - tg);
            String message = player.getFactionEmoji() + " reduced trade goods by " + tg +
                " (trade goods: " + (player.getTg() + tg) + "->" + player.getTg() + ").";
            ReactionService.addReaction(event, game, player, message);
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "You don't have enough trade goods. You have " + player.getTg() + " trade goods.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resetSpend_")
    public static void resetSpend_(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planetName = buttonID.replace("resetSpend_", "");
        String message = Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " was refreshed.";

        if (player.refreshPlanet(planetName)) {
            ReactionService.addReaction(event, game, player, message);
        } else {
            message = "Planet " + planetName + " not found or already refreshed.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resetSpend")
    public static void resetSpend(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        List<String> spentThings = ButtonHelper.getSpentThingsFromButtonID(buttonID);
        for (String spentThing : spentThings) {
            if (spentThing.contains("planet_")) {
                String planetName = spentThing.replace("planet_", "");
                player.refreshPlanet(planetName);
            }
        }

        String message = player.getFactionEmoji() + " reset their spent resources.";
        ButtonHelper.addReaction(event, false, false, message, "");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("mallice_convert_comm")
    public static void malliceConvertComm(ButtonInteractionEvent event, Player player, Game game) {
        String playerRep = player.getFactionEmoji();
        int commod = player.getCommodities();
        String message = playerRep + " exhausted Mallice ability to convert their " + commod
            + " commodit" + (commod == 1 ? "y" : "ies") + " to "
            + (commod == 1 ? "a trade good" : commod + " trade goods") + " (trade goods: "
            + player.getTg() + "->" + (player.getTg() + commod) + ").";
        player.setTg(player.getTg() + commod);
        player.setCommodities(0);
        if (!game.isFowMode() && event.getMessageChannel() != game.getMainGameChannel()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("mallice_2_tg")
    public static void mallice2tg(ButtonInteractionEvent event, Player player, Game game) {
        String playerRep = player.getFactionEmoji();
        String message = playerRep + " exhausted Mallice ability and gained trade goods " + player.gainTG(2) + ".";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 2);
        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        if (!game.isFowMode() && event.getMessageChannel() != game.getMainGameChannel()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    public static void gain1TG(ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = "";
        String labelP = event.getButton().getLabel();
        String planetName = labelP.substring(labelP.lastIndexOf(" ") + 1);
        boolean failed = false;
        if (labelP.contains("inf") && labelP.contains("mech")) {
            message += "Please resolve removing infantry manually, if applicable.";
            failed = message.contains("Please try again.");
        }
        if (!failed) {
            message += "Gained 1 trade good " + player.gainTG(1, true) + ".";
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
        }
        ReactionService.addReaction(event, game, player, message);
        if (!failed) {
            ButtonHelper.deleteMessage(event);
            if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
                String pF = player.getFactionEmoji();
                MessageHelper.sendMessageToChannel(mainGameChannel, pF + " " + message);
            }
        }
    }

    @ButtonHandler("resFrontier_")
    public static void resFrontier(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.split("_")[1];
        String message = player.getFactionEmoji() + " gained 1 trade good " + player.gainTG(1) +
            " from " + Helper.getPlanetRepresentation(planet, game) + " (frontier card).";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    public static void gain1tgFromMuaatCommander(
        ButtonInteractionEvent event, Player player, Game game,
        MessageChannel mainGameChannel
    ) {
        String message = player.getRepresentation() + " gained 1 trade good " + player.gainTG(1)
            + " from Magmus, the Muaat commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    public static void gain1tgFromLetnevCommander(
        ButtonInteractionEvent event, Player player, Game game,
        MessageChannel mainGameChannel
    ) {
        String message = player.getRepresentation() + " gained 1 trade good " + player.gainTG(1)
            + " from Rear Admiral Farran, the Letnev commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }
}