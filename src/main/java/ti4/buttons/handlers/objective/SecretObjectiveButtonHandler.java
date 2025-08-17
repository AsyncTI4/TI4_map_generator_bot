package ti4.buttons.handlers.objective;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.milty.MiltyDraftManager;
import ti4.service.objectives.DiscardSecretService;
import ti4.service.objectives.DrawSecretService;
import ti4.settings.users.UserSettingsManager;

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

            String msg = player.getRepresentation() + " discarded a secret objective";
            if (game.getRound() == 1 && !game.isFowMode()) {
                int amountLeftToDiscard = -1;
                for (Player p2 : game.getRealPlayers()) {
                    if (p2.getSo() > 1) {
                        amountLeftToDiscard++;
                    }
                }
                msg += " (" + amountLeftToDiscard + " player" + (amountLeftToDiscard == 1 ? "" : "s")
                        + " still to discard)";
            }
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg + ".");
            DiscardSecretService.discardSO(player, soIndex, game);

            if (drawReplacement) {
                DrawSecretService.drawSO(event, game, player);
            }
            if (game.getRound() == 1 && !game.isFowMode() && !game.isCommunityMode()) {
                var userSettings = UserSettingsManager.get(player.getUserID());
                if (!userSettings.isHasAnsweredSurvey()) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green("answerSurvey_yes_1", "Yes"));
                    buttons.add(Buttons.red("deleteButtons", "No"));
                    String msg2 = player.getRepresentation()
                            + " Hullo there! Welcome to async! As part of the ground rules setup process here, we request each player complete a 1 time survey of 5 questions. Would you like to complete it now?";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg2, buttons);
                }
            }
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(event, player), "Could not parse SO ID: " + soID, e);
            event.getChannel()
                    .sendMessage("Could not parse secret objective ID: " + soID + ". Please discard manually.")
                    .queue();
            return;
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("drawSpecificSO_")
    public static void drawSpecificSO(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String soID = buttonID.split("_")[1];
        String publicMsg = game.getPing() + " this is notice that " + player.getFactionEmojiOrColor()
                + " is picking up a secret objective that they accidentally discarded.";
        Map<String, Integer> secrets = game.drawSpecificSecretObjective(soID, player.getUserID());
        if (secrets == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    "Secret objective not retrieved, most likely because someone else has it in hand. Ping a bothelper to help.");
            return;
        }
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), publicMsg);
        ButtonHelper.deleteMessage(event);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
    }

    @ButtonHandler("deal2SOToAll")
    public static void deal2SOToAll(ButtonInteractionEvent event, Game game, Player player) {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        if (manager.isFinished()
                && manager.isFactionTaken("keleresm")
                && game.getPlayerFromColorOrFaction("keleres") == null) {
            Player keleres = null;
            for (String playerID : manager.getPlayers())
                if ("keleresm".equals(manager.getPlayerDraft(playerID).getFaction()))
                    keleres = game.getPlayer(playerID);
            if (keleres != null) {
                MessageHelper.sendMessageToChannel(
                        keleres.getCorrectChannel(),
                        "Keleres is not set up yet!!! " + keleres.getPing()
                                + ", the game is waiting for you to set up :).");
                return;
            }
        }
        boolean allPlayersSetup = true;
        StringBuilder message = new StringBuilder(
                "🛑 Cannot deal secret objectives yet as some players still need to pick their starting technologies. If you wish to proceed anyways, just press the button again.");
        for (Player p : game.getRealPlayers()) {
            if (p.getTechs().size() < p.getFactionModel().finalStartingTechAmount()) {
                message.append("\n> ").append(p.getRepresentation());
                allPlayersSetup = false;
            }
        }
        if (!allPlayersSetup && !game.getStoredValue("overrideSORes").contains(player.getFaction())) {
            game.setStoredValue("overrideSORes", game.getStoredValue("overrideSORes") + player.getFaction());
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
            return;
        }

        DrawSecretService.dealSOToAll(event, 2, game);
        ButtonHelper.deleteMessage(event);
    }
}
