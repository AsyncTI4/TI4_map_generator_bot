package ti4.discord.interactions.buttons.handlers.objective;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.StringHelper;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.MessageHelper;
import ti4.model.SecretObjectiveModel;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.map.FractureService;
import ti4.service.milty.MiltyDraftManager;
import ti4.service.objectives.DiscardSecretService;
import ti4.service.objectives.DrawSecretService;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
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
            SecretObjectiveModel secretObjective = player.getSecret(soIndex);
            if (secretObjective == null) {
                MessageHelper.sendMessageToPlayerCardsInfoThread(
                        player, "No such secret objective ID found, please retry.");
                return;
            }

            String msg = player.getRepresentationNoPing() + " discarded a secret objective";
            if (game.getRound() == 1 && !game.isFowMode() && player.getSo() > 1) {
                int amountLeftToDiscard = -1;
                for (Player p2 : game.getRealPlayers()) {
                    if (p2.getSo() > 1 && (!game.isErwansGambitMode() || !"mentak".equalsIgnoreCase(p2.getFaction()))) {
                        amountLeftToDiscard++;
                    }
                }
                if (amountLeftToDiscard > -1) {
                    msg += " (" + StringHelper.pluralize(amountLeftToDiscard, "player") + " still to discard)";
                }
            }
            if (!DiscardSecretService.discardSO(player, soIndex, game)) {
                return;
            }
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg + ".");

            if (drawReplacement) {
                String oldSO = secretObjective.getAlias();
                DrawSecretService.drawSO(event, game, player);

                if (player.getSecrets().containsKey(oldSO)
                        && event.getUser().getId().equals(player.getUserID())) {
                    event.getHook()
                            .setEphemeral(true)
                            .sendMessage("Oh no! You drew the same secret you discarded! How unlucky 😭😭😭!")
                            .queue(Consumers.nop(), BotLogger::catchRestError);
                }
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation() + " drew a replacement secret objective.");
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
            BotLogger.error(new LogOrigin(event, player), "Could not parse SO ID: " + soID, e);
            event.getChannel()
                    .sendMessage("Could not parse secret objective ID: " + soID + ". Please discard manually.")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
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

    @ButtonHandler(value = "get_so_discard_buttons", save = false)
    public static void getSODiscardButtons(ButtonInteractionEvent event, Player player) {
        String secretScoreMsg = "Click a button below to discard your secret objective.";
        List<Button> soButtons = SecretObjectiveHelper.getUnscoredSecretObjectiveDiscardButtons(player);
        if (!soButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), secretScoreMsg, soButtons);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong. Please report to Developers");
        }
    }

    @ButtonHandler("deal2SOToAll")
    public static void deal2SOToAll(ButtonInteractionEvent event, Game game) {
        Player unsetKeleres = findUnsetKeleres(game);
        List<String> waitingOn = new ArrayList<>();
        if (unsetKeleres != null) {
            waitingOn.add(unsetKeleres.getRepresentationNoPing() + " still needs to set up as Keleres");
        }
        for (Player p : game.getRealPlayers()) {
            if (p.getTechs().size() < p.getFactionModel().finalStartingTechAmount()) {
                waitingOn.add(p.getRepresentation() + " still needs to choose their starting technologies");
            }
        }
        for (Player p : FractureService.playersWithPendingRoll(game)) {
            waitingOn.add(p.getRepresentation() + " still needs to roll for The Fracture");
        }

        if (!waitingOn.isEmpty()) {
            String message = "🛑 Cannot deal secret objectives yet as some players have not finished setting up.\n> "
                    + String.join("\n> ", waitingOn);
            if (unsetKeleres != null) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                return;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    message,
                    List.of(Buttons.red("deal2SOToAllAnyway_" + event.getMessageId(), "Deal Anyway")));
            return;
        }

        DrawSecretService.dealSOToAll(event, 2, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("deal2SOToAllAnyway_")
    public static void deal2SOToAllAnyway(ButtonInteractionEvent event, Game game, String buttonID) {
        if (findUnsetKeleres(game) != null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "🛑 Keleres still needs to set up, so secret objectives cannot be dealt.");
            return;
        }
        String dealButtonMessageId = buttonID.replace("deal2SOToAllAnyway_", "");
        event.getMessageChannel()
                .deleteMessageById(dealButtonMessageId)
                .queue(Consumers.nop(), BotLogger::catchRestError);
        DrawSecretService.dealSOToAll(event, 2, game);
        ButtonHelper.deleteMessage(event);
    }

    private static Player findUnsetKeleres(Game game) {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        if (!manager.isFinished()
                || !manager.isFactionTaken("keleresm")
                || game.getPlayerFromColorOrFaction("keleres") != null) {
            return null;
        }
        for (String playerID : manager.getPlayers()) {
            if ("keleresm".equals(manager.getPlayerDraft(playerID).getFaction())) {
                return game.getPlayer(playerID);
            }
        }
        return null;
    }
}
