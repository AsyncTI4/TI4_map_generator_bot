package ti4.discord.interactions.buttons.handlers.agenda;

import java.util.ArrayList;
import java.util.HashSet;
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
import ti4.game.Tile;
import ti4.helpers.AgendaRiderHelper;
import ti4.helpers.AgendaSummaryHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;

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
                if (game.isFowMode()) {
                    // Picking a player and then being shown their entire planet list is the leak. Offer the
                    // planets this voter already knows about, plus anything already voted for (those are
                    // public in the vote summary and must stay selectable), plus Blind Target.
                    voteMessage = player.getRepresentation()
                            + " is up to vote. Please choose the planet you wish to vote for.";
                    outcomeActionRow = fogPlanetOutcomeButtons(game, player, "outcome");
                } else {
                    voteMessage = player.getRepresentation() + " is up to vote."
                            + " Since there are too many planets in the game to represent all as buttons,"
                            + " please use the buttons to choose the player who controls the planet you wish to vote for."
                            + " You will then be given a list of their planets to vote for.";
                    outcomeActionRow = AgendaRiderHelper.getPlayerOutcomeButtons(game, null, "planetOutcomes", null);
                }
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

    // Non-fog only: the fog path never offers the player-picking step that leads here.
    @ButtonHandler("planetOutcomes_")
    static void planetOutcomes(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String factionOrColor = buttonID.substring(buttonID.indexOf('_') + 1);
        Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
        String voteMessage = "Choosing to vote for one of " + factionOrColor
                + "'s planets. Please use the buttons to choose the planet you wish to vote for.";
        List<Button> outcomeActionRow;
        if (game.isFowMode()) {
            if (player == null) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve that player.");
                return;
            }
            voteMessage = "Please choose the planet you wish to vote for.";
            outcomeActionRow = fogPlanetOutcomeButtons(game, player, "outcome");
        } else if (planetOwner == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve that player.");
            return;
        } else {
            outcomeActionRow = getPlanetOutcomeButtons(planetOwner, game, "outcome", null);
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("tiedPlanets_")
    static void tiedPlanets(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        buttonID = buttonID.replace("tiedPlanets_", "");
        buttonID = buttonID.replace("resolveAgendaVote_outcomeTie*_", "");
        buttonID = buttonID.replace("agendaResolution_", "");
        String factionOrColor = buttonID;
        Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
        String voteMessage = "Choosing to break tie for one of " + factionOrColor
                + "'s planets. As Speaker, please decide a winner.";

        List<Button> outcomeActionRow;
        if (game.isFowMode()) {
            // Reached when nobody voted, and from manual resolution. "Any planet" is rules-correct here, but
            // it must still be any planet the chooser could know about rather than everyone's holdings.
            // This id carries no FFCC_ gate, so anyone can press it - including someone who is not a seated
            // player, whose fog knowledge does not exist.
            if (player == null) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve that player.");
                return;
            }
            voteMessage = "As Speaker, please decide a winner.";
            outcomeActionRow = fogPlanetOutcomeButtons(game, player, "resolveAgendaVote_outcomeTie*");
        } else if (planetOwner == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve that player.");
            return;
        } else {
            outcomeActionRow = getPlanetOutcomeButtons(planetOwner, game, "resolveAgendaVote_outcomeTie*", null);
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
        ButtonHelper.deleteMessage(event);
    }

    /**
     * The fog-safe candidate list for an Elect Planet outcome.
     *
     * <p>Two sources: planets the voter could know exist, and planets already named in this agenda's votes.
     * The second set matters for correctness rather than secrecy - the vote summary shows later voters which
     * outcomes have been picked, so those must remain selectable even if the voter has never seen the planet.
     *
     * <p>Safe to relabel: an outcome is keyed by the raw planet id, and the summary renders it through
     * {@code getAgendaOutcomeName}, so nothing downstream depends on this button's text.
     */
    static List<Button> fogPlanetOutcomeButtons(Game game, Player player, String prefix) {
        // A button pressed after the agenda window closed has no agenda info to read. AgendaHelper.autoResolve
        // guards the same expression the same way. Empty is an unambiguous signal to the callers below,
        // because a live fog list always carries at least the Blind Target button.
        String[] agendaInfo = game.getCurrentAgendaInfo() == null
                ? new String[0]
                : game.getCurrentAgendaInfo().split("_");
        if (agendaInfo.length < 2) return List.of();
        String agendaDetails = agendaInfo[1].toLowerCase();
        boolean nonHome = agendaDetails.contains("non-home");
        var spec = PlanetTargetSpec.of(prefix)
                .where(planet -> {
                    if (planet.isSpaceStation(game)) return false;
                    if (!nonHome) return true;
                    Tile tile = game.getTileFromPlanet(planet.getName());
                    if (tile != null && tile.isHomeSystem(game)) return false;
                    return !"mrte".equalsIgnoreCase(planet.getName()) && !"mr".equalsIgnoreCase(planet.getName());
                })
                .withAlwaysInclude(new HashSet<>(game.getCurrentAgendaVotes().keySet()));
        return PlanetTargetService.targetButtons(game, player, spec, new ArrayList<>());
    }

    private static List<Button> getPlanetOutcomeButtons(Player planetOwner, Game game, String prefix, String rider) {
        List<Button> planetOutcomeButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(planetOwner.getPlanets());
        for (String planet : planets) {
            Planet p = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            if (p != null && p.isSpaceStation(game)) continue;
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
