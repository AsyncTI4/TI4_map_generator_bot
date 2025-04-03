package ti4.buttons.handlers.objective;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.objectives.DiscardSecretService;
import ti4.service.objectives.DrawSecretService;

class SecretObjectiveButtonHandler {

    @ButtonHandler("SODISCARD_")
    @ButtonHandler("discardSecret_")
    private static void discardSecretButton(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String soID = buttonID.replace("SODISCARD_", "");
        soID = soID.replace("discardSecret_", "");

        boolean drawReplacement = false;
        if (soID.endsWith("redraw")) {
            soID = soID.replace("redraw", "");
            drawReplacement = true;
        }

        try {
            int soIndex = Integer.parseInt(soID);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " discarded a secret objective.");
            DiscardSecretService.discardSO(player, soIndex, game);
            if (drawReplacement) {
                DrawSecretService.drawSO(event, game, player);
            }
        } catch (Exception e) {
            BotLogger.error(event, "Could not parse SO ID: " + soID, e, true);
            event.getChannel().sendMessage("Could not parse secret objective ID: " + soID + ". Please discard manually.").queue();
            return;
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("drawSpecificSO_")
    public static void drawSpecificSO(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String soID = buttonID.split("_")[1];
        String publicMsg = game.getPing() + " this is notice that " + player.getFactionEmojiOrColor() + " is picking up a secret objective that they accidentally discarded.";
        Map<String, Integer> secrets = game.drawSpecificSecretObjective(soID, player.getUserID());
        if (secrets == null) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Secret objective not retrieved, most likely because someone else has it in hand. Ping a bothelper to help.");
            return;
        }
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), publicMsg);
        ButtonHelper.deleteMessage(event);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
    }

    @ButtonHandler("deal2SOToAll")
    public static void deal2SOToAll(ButtonInteractionEvent event, Game game) {
        DrawSecretService.dealSOToAll(event, 2, game);
        ButtonHelper.deleteMessage(event);
    }
}
