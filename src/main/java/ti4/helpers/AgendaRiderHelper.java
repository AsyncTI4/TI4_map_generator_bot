package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
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

@UtilityClass
public class AgendaRiderHelper {

    @ButtonHandler("reverse_")
    public static void reverseRider(String buttonID, Game game, Player player) {
        String choice = buttonID.substring(buttonID.indexOf('_') + 1);
        String voteMessage = player.getFactionEmojiOrColor() + " Chose to reverse the " + choice + ".";

        rewriteVotes(game, piece -> !piece.contains(choice), null);

        player.getCorrectChannel().sendMessage(voteMessage).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("eraseMyRiders")
    public static void reverseAllRiders(Game game, Player player) {
        if (player.hasAbility("galactic_threat")) {
            game.removeStoredValue("galacticThreatUsed");
        }
        rewriteVotes(
                game,
                piece -> {
                    String identifier = piece.split("_")[0];
                    return !identifier.equalsIgnoreCase(player.getFaction())
                            && !identifier.equalsIgnoreCase(player.getColor());
                },
                erased -> MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(), player.getFactionEmoji() + " erased " + erased.split("_")[1]));
    }

    /**
     * Filters each outcome's voting pieces, keeping only those matching {@code keep}.
     * Removed pieces are passed to {@code onRemove} (may be null).
     */
    private static void rewriteVotes(
            Game game, java.util.function.Predicate<String> keep, java.util.function.Consumer<String> onRemove) {
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        for (String outcome : outcomes.keySet()) {
            String existingData = outcomes.getOrDefault(outcome, "empty");
            if (existingData == null || "empty".equalsIgnoreCase(existingData) || existingData.isEmpty()) {
                continue;
            }
            StringBuilder totalBuilder = new StringBuilder();
            for (String onePiece : existingData.split(";")) {
                if (keep.test(onePiece)) {
                    totalBuilder.append(";").append(onePiece);
                } else if (onRemove != null) {
                    onRemove.accept(onePiece);
                }
            }
            String total = totalBuilder.toString();
            if (!total.isEmpty() && total.charAt(0) == ';') {
                total = total.substring(1);
            }
            game.setCurrentAgendaVote(outcome, total);
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
        String rider = buttonID.substring(lastIndex + 1);
        String agendaDetails = game.getCurrentAgendaInfo().split("_")[1];

        String cleanedChoice = choice;
        if (agendaDetails.toLowerCase().contains("planet")) {
            cleanedChoice = Helper.getPlanetRepresentation(choice, game);
        }
        String voteMessage = "chose to put a " + rider + " on \"" + StringUtils.capitalize(cleanedChoice) + "\".";
        voteMessage = !game.isFowMode()
                ? player.getRepresentationNoPing() + " " + voteMessage
                : StringUtils.capitalize(voteMessage);

        String identifier = game.isFowMode() ? player.getColor() : player.getFaction();

        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        String existingData = outcomes.getOrDefault(choice, "empty");
        existingData = "empty".equalsIgnoreCase(existingData)
                ? identifier + "_" + rider
                : existingData + ";" + identifier + "_" + rider;
        game.setCurrentAgendaVote(choice, existingData);

        MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
        String summary = AgendaSummaryHelper.getSummaryOfVotes(game, true);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), summary + "\n \n");

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", don't forget you now have to decide on whether you will play any more \"after\"s.");
    }

    private static List<Button> getPlanetOutcomeButtons(Player player, Game game, String prefix, String rider) {
        List<Button> planetOutcomeButtons = new ArrayList<>();
        for (String planet : new ArrayList<>(player.getPlanets())) {
            String label = Helper.getPlanetRepresentation(planet, game);
            Button button = rider == null
                    ? Buttons.gray(prefix + "_" + planet, label)
                    : Buttons.gray(prefix + "rider_planet;" + planet + "_" + rider, label);
            planetOutcomeButtons.add(button);
        }
        return planetOutcomeButtons;
    }

    public static List<Button> getAgendaButtons(String riderName, Game game, String prefix) {
        String agendaDetails = game.getCurrentAgendaInfo().split("_")[1];
        String lower = agendaDetails.toLowerCase();

        if (agendaDetails.contains("For")) {
            return VoteButtonHandler.getForAgainstOutcomeButtons(
                    game, riderName, prefix, game.getCurrentAgendaInfo().split("_")[2], null);
        }
        if (lower.contains("player")) {
            return VoteButtonHandler.getPlayerOutcomeButtons(game, riderName, prefix, null);
        }
        if (lower.contains("planet")) {
            return riderName == null
                    ? VoteButtonHandler.getPlayerOutcomeButtons(game, null, "tiedPlanets_" + prefix, "planetRider")
                    : VoteButtonHandler.getPlayerOutcomeButtons(game, riderName, prefix, "planetRider");
        }
        if (lower.contains("secret")) {
            return VoteButtonHandler.getSecretOutcomeButtons(game, riderName, prefix);
        }
        if (lower.contains("strategy")) {
            return VoteButtonHandler.getStrategyOutcomeButtons(game, riderName, prefix);
        }
        if (agendaDetails.contains("unit upgrade")) {
            return VoteButtonHandler.getUnitUpgradeOutcomeButtons(game, riderName, prefix);
        }
        if (lower.contains("unit")) {
            return VoteButtonHandler.getUnitOutcomeButtons(game, riderName, prefix);
        }
        return VoteButtonHandler.getLawOutcomeButtons(game, riderName, prefix);
    }

    @ButtonHandler("planetRider_")
    public static void planetRider(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        buttonID = buttonID.replace("planetRider_", "");
        String factionOrColor = buttonID.substring(0, buttonID.indexOf('_'));
        Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
        String voteMessage = "Chose to Rider for one of " + factionOrColor + "'s planets. Please choose which one.";

        String rider = buttonID.replace(factionOrColor + "_", "");
        List<Button> outcomeActionRow =
                getPlanetOutcomeButtons(planetOwner, game, player.factionButtonChecker(), rider);

        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
        ButtonHelper.deleteMessage(event);
    }
}
