package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.agenda.VoteButtonHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.MessageHelper;

public final class AgendaRiderHelper {

    private AgendaRiderHelper() {}

    @ButtonHandler("reverse_")
    public static void reverseRider(String buttonID, Game game, Player player) {
        String choice = buttonID.substring(buttonID.indexOf('_') + 1);
        String voteMessage = player.getFactionEmojiOrColor() + " Chose to reverse the " + choice + ".";
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        for (String outcome : outcomes.keySet()) {
            String existingData = outcomes.getOrDefault(outcome, "empty");
            if (existingData != null && !"empty".equalsIgnoreCase(existingData) && !"".equalsIgnoreCase(existingData)) {
                String[] votingInfo = existingData.split(";");
                StringBuilder totalBuilder = new StringBuilder();
                for (String onePiece : votingInfo) {
                    if (!onePiece.contains(choice)) {
                        totalBuilder.append(";").append(onePiece);
                    }
                }
                String total = totalBuilder.toString();
                if (!total.isEmpty() && total.charAt(0) == ';') {
                    total = total.substring(1);
                }
                game.setCurrentAgendaVote(outcome, total);
            }
        }
        player.getCorrectChannel().sendMessage(voteMessage).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("eraseMyRiders")
    public static void reverseAllRiders(Game game, Player player) {
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        if (player.hasAbility("galactic_threat")) {
            game.removeStoredValue("galacticThreatUsed");
        }
        for (String outcome : outcomes.keySet()) {
            String existingData = outcomes.getOrDefault(outcome, "empty");
            if (existingData != null && !"empty".equalsIgnoreCase(existingData) && !"".equalsIgnoreCase(existingData)) {
                String[] votingInfo = existingData.split(";");
                StringBuilder totalBuilder = new StringBuilder();
                for (String onePiece : votingInfo) {
                    String identifier = onePiece.split("_")[0];
                    if (!identifier.equalsIgnoreCase(player.getFaction())
                            && !identifier.equalsIgnoreCase(player.getColor())) {
                        totalBuilder.append(";").append(onePiece);
                    } else {
                        MessageHelper.sendMessageToChannel(
                                player.getCorrectChannel(),
                                player.getFactionEmoji() + " erased " + onePiece.split("_")[1]);
                    }
                }
                String total = totalBuilder.toString();
                if (!total.isEmpty() && total.charAt(0) == ';') {
                    total = total.substring(1);
                }
                game.setCurrentAgendaVote(outcome, total);
            }
        }
    }

    @ButtonHandler("rider_")
    public static void placeRider(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        int firstIndex = buttonID.indexOf('_');
        int lastIndex = buttonID.lastIndexOf('_');
        if (firstIndex == -1 || lastIndex <= firstIndex) {
            BotLogger.error(new LogOrigin(event, game), "Could not parse rider info from button id: " + buttonID);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not parse rider choice.");
            return;
        }
        String[] choiceParams = buttonID.substring(firstIndex + 1, lastIndex).split(";");
        if (choiceParams.length < 2) {
            BotLogger.error(new LogOrigin(event, game), "Invalid rider parameters in button id: " + buttonID);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not parse rider choice.");
            return;
        }
        String choice = choiceParams[1];

        String rider = buttonID.substring(buttonID.lastIndexOf('_') + 1);
        String agendaDetails = game.getCurrentAgendaInfo().split("_")[1];

        String cleanedChoice = choice;
        if (agendaDetails.contains("Planet") || agendaDetails.contains("planet")) {
            cleanedChoice = Helper.getPlanetRepresentation(choice, game);
        }
        String voteMessage = "chose to put a " + rider + " on \"" + StringUtils.capitalize(cleanedChoice) + "\".";
        voteMessage = !game.isFowMode()
                ? player.getRepresentationNoPing() + " " + voteMessage
                : StringUtils.capitalize(voteMessage);

        String identifier;
        if (game.isFowMode()) {
            identifier = player.getColor();
        } else {
            identifier = player.getFaction();
        }
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        String existingData = outcomes.getOrDefault(choice, "empty");
        if ("empty".equalsIgnoreCase(existingData)) {
            existingData = identifier + "_" + rider;
        } else {
            existingData += ";" + identifier + "_" + rider;
        }
        game.setCurrentAgendaVote(choice, existingData);

        MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
        String summary2 = AgendaSummaryHelper.getSummaryOfVotes(game, true);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), summary2 + "\n \n");

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", don't forget you now have to decide on whether you will play any more \"after\"s.");
    }

    private static List<Button> getPlanetOutcomeButtons(Player player, Game game, String prefix, String rider) {
        List<Button> planetOutcomeButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets());
        for (String planet : planets) {
            Button button;
            if (rider == null) {
                button = Buttons.gray(prefix + "_" + planet, Helper.getPlanetRepresentation(planet, game));
            } else {
                button = Buttons.gray(
                        prefix + "rider_planet;" + planet + "_" + rider, Helper.getPlanetRepresentation(planet, game));
            }
            planetOutcomeButtons.add(button);
        }
        return planetOutcomeButtons;
    }

    public static List<Button> getAgendaButtons(String riderName, Game game, String prefix) {
        String agendaDetails = game.getCurrentAgendaInfo().split("_")[1];
        List<Button> outcomeActionRow;
        if (agendaDetails.contains("For")) {
            outcomeActionRow = VoteButtonHandler.getForAgainstOutcomeButtons(
                    game, riderName, prefix, game.getCurrentAgendaInfo().split("_")[2], null);
        } else if (agendaDetails.contains("Player") || agendaDetails.contains("player")) {
            outcomeActionRow = VoteButtonHandler.getPlayerOutcomeButtons(game, riderName, prefix, null);
        } else if (agendaDetails.contains("Planet") || agendaDetails.contains("planet")) {
            if (riderName == null) {
                outcomeActionRow =
                        VoteButtonHandler.getPlayerOutcomeButtons(game, null, "tiedPlanets_" + prefix, "planetRider");
            } else {
                outcomeActionRow = VoteButtonHandler.getPlayerOutcomeButtons(game, riderName, prefix, "planetRider");
            }
        } else if (agendaDetails.contains("Secret") || agendaDetails.contains("secret")) {
            outcomeActionRow = VoteButtonHandler.getSecretOutcomeButtons(game, riderName, prefix);
        } else if (agendaDetails.contains("Strategy") || agendaDetails.contains("strategy")) {
            outcomeActionRow = VoteButtonHandler.getStrategyOutcomeButtons(game, riderName, prefix);
        } else if (agendaDetails.contains("unit upgrade")) {
            outcomeActionRow = VoteButtonHandler.getUnitUpgradeOutcomeButtons(game, riderName, prefix);
        } else if (agendaDetails.contains("Unit") || agendaDetails.contains("unit")) {
            outcomeActionRow = VoteButtonHandler.getUnitOutcomeButtons(game, riderName, prefix);
        } else {
            outcomeActionRow = VoteButtonHandler.getLawOutcomeButtons(game, riderName, prefix);
        }

        return outcomeActionRow;
    }

    @ButtonHandler("planetRider_")
    public static void planetRider(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        buttonID = buttonID.replace("planetRider_", "");
        String factionOrColor = buttonID.substring(0, buttonID.indexOf('_'));
        Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
        String voteMessage = "Chose to Rider for one of " + factionOrColor + "'s planets. Please choose which one.";
        List<Button> outcomeActionRow;
        buttonID = buttonID.replace(factionOrColor + "_", "");
        outcomeActionRow = getPlanetOutcomeButtons(planetOwner, game, player.factionButtonChecker(), buttonID);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
        ButtonHelper.deleteMessage(event);
    }
}
