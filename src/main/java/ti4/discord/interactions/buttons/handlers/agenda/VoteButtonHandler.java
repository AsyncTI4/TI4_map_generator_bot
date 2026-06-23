package ti4.discord.interactions.buttons.handlers.agenda;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.AgendaRiderHelper;
import ti4.helpers.AgendaSummaryHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;

@UtilityClass
class VoteButtonHandler {

    @ButtonHandler("erasePreVote")
    static void erasePreVote(GenericInteractionCreateEvent event, Player player, Game game) {
        game.setStoredValue("preVoting" + player.getFaction(), "");
        player.resetSpentThings();
        if (event instanceof ButtonInteractionEvent bEvent) {
            bEvent.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("preVote", "Pre-Vote"));
        buttons.add(Buttons.blue("resolvePreassignment_Abstain On Agenda", "Pre-abstain"));
        buttons.add(Buttons.red("deleteButtons", "Don't do anything"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Erased the pre-vote", buttons);
    }

    @ButtonHandler("preVote")
    static void preVote(ButtonInteractionEvent event, Player player, Game game) {
        game.setStoredValue("preVoting" + player.getFaction(), "0");
        firstStepOfVoting(game, event, player);
    }

    @ButtonHandler("vote")
    private static void firstStepOfVoting(Game game, ButtonInteractionEvent event, Player player) {
        String pfaction2 = null;
        if (player != null) {
            pfaction2 = player.getFaction();
        }
        if (pfaction2 != null) {
            String voteMessage = player.getRepresentation()
                    + " is up to vote. Please use the buttons to choose the outcome you wish to vote for.";
            String agendaDetails = game.getCurrentAgendaInfo().split("_")[1];
            List<Button> outcomeActionRow;
            if (agendaDetails.contains("For") || agendaDetails.contains("for")) {
                outcomeActionRow = AgendaRiderHelper.getForAgainstOutcomeButtons(
                        game, null, "outcome", game.getCurrentAgendaInfo().split("_")[2], player);
            } else if (agendaDetails.contains("Player") || agendaDetails.contains("player")) {
                outcomeActionRow = AgendaRiderHelper.getPlayerOutcomeButtons(game, null, "outcome", null);
            } else if (agendaDetails.contains("Planet") || agendaDetails.contains("planet")) {
                voteMessage = player.getRepresentation() + " is up to vote."
                        + " Since there are too many planets in the game to represent all as buttons,"
                        + " please use the buttons to choose the player who controls the planet you wish to vote for."
                        + " You will then be given a list of their planets to vote for.";
                outcomeActionRow = AgendaRiderHelper.getPlayerOutcomeButtons(game, null, "planetOutcomes", null);
            } else if (agendaDetails.contains("Secret") || agendaDetails.contains("secret")) {
                outcomeActionRow = AgendaRiderHelper.getSecretOutcomeButtons(game, null, "outcome");
            } else if (agendaDetails.contains("Strategy") || agendaDetails.contains("strategy")) {
                outcomeActionRow = AgendaRiderHelper.getStrategyOutcomeButtons(game, null, "outcome");
            } else if (agendaDetails.contains("unit upgrade")) {
                outcomeActionRow = AgendaRiderHelper.getUnitUpgradeOutcomeButtons(game, null, "outcome");
            } else if (agendaDetails.contains("Unit") || agendaDetails.contains("unit")) {
                outcomeActionRow = AgendaRiderHelper.getUnitOutcomeButtons(game, null, "outcome");
            } else {
                outcomeActionRow = AgendaRiderHelper.getLawOutcomeButtons(game, null, "outcome");
            }
            if (!game.getStoredValue("agendaChecksNBalancesAgainst").isEmpty()) {
                MessageHelper.sendEphemeralMessageToEventChannel(
                        event,
                        "**Reminder: _Checks and Balances_ has resolved \"Against\" — you will only be able to ready 3 planets at the end of this agenda phase.**");
            }
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getChannel(),
                    AgendaSummaryHelper.getSummaryOfVotes(game, true) + "\n\n" + voteMessage,
                    outcomeActionRow);
        }
    }

    @ButtonHandler("planetOutcomes_")
    static void planetOutcomes(ButtonInteractionEvent event, String buttonID, Game game) {
        String factionOrColor = buttonID.substring(buttonID.indexOf('_') + 1);
        Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
        String voteMessage = "Choosing to vote for one of " + factionOrColor
                + "'s planets. Please use the buttons to choose the planet you wish to vote for.";
        List<Button> outcomeActionRow;
        outcomeActionRow = getPlanetOutcomeButtons(planetOwner, game, "outcome", null);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("tiedPlanets_")
    static void tiedPlanets(ButtonInteractionEvent event, String buttonID, Game game) {
        buttonID = buttonID.replace("tiedPlanets_", "");
        buttonID = buttonID.replace("resolveAgendaVote_outcomeTie*_", "");
        buttonID = buttonID.replace("agendaResolution_", "");
        String factionOrColor = buttonID;
        Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
        String voteMessage = "Choosing to break tie for one of " + factionOrColor
                + "'s planets. As Speaker, please decide a winner.";

        List<Button> outcomeActionRow;
        outcomeActionRow = getPlanetOutcomeButtons(planetOwner, game, "resolveAgendaVote_outcomeTie*", null);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getPlanetOutcomeButtons(Player player, Game game, String prefix, String rider) {
        List<Button> planetOutcomeButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets());
        for (String planet : planets) {
            Planet p = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            if (p != null && p.isSpaceStation()) continue;
            String agendaDetails = game.getCurrentAgendaInfo().split("_")[1].toLowerCase();
            if (agendaDetails.contains("non-home")) {
                if (game.getTileFromPlanet(planet) != null
                        && game.getTileFromPlanet(planet).isHomeSystem(game)) {
                    continue;
                }
                if ("mrte".equalsIgnoreCase(planet) || "mr".equalsIgnoreCase(planet)) {
                    continue;
                }
            }
            Button button;
            TI4Emoji planetEmoji = PlanetEmojis.getPlanetEmoji(planet);
            if (rider == null) {
                button = Buttons.blue(prefix + "_" + planet, Helper.getPlanetRepresentation(planet, game), planetEmoji);
            } else {
                button = Buttons.blue(
                        prefix + "rider_planet;" + planet + "_" + rider,
                        Helper.getPlanetRepresentation(planet, game),
                        planetEmoji);
            }
            planetOutcomeButtons.add(button);
        }
        return planetOutcomeButtons;
    }
}
