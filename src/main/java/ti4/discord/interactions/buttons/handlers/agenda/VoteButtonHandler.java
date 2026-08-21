package ti4.discord.interactions.buttons.handlers.agenda;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
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
import ti4.helpers.NewStuffHelper;
import ti4.helpers.RegexHelper;
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
            // A stale Vote button - pressed after the agenda window closed, e.g. from an old message - has no
            // agenda info to read. Same guard AgendaHelper.autoResolve uses for the same reason.
            if (game.getCurrentAgendaInfo().split("_").length < 2) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "This agenda resolution window has closed.");
                ButtonHelper.deleteMessage(event);
                return;
            }
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

    // Non-fog only. firstStepOfVoting is this button's one and only generator, and it only builds a
    // planetOutcomes_ button from its non-fog branch - in fog it calls fogPlanetOutcomeButtons directly and
    // skips this step entirely. Unlike tiedPlanets_ below, nothing else in the codebase builds this id, and
    // fog mode is fixed at game creation (no live toggle exists), so there is no real path that reaches this
    // method with fog on.
    @ButtonHandler("planetOutcomes_")
    static void planetOutcomes(ButtonInteractionEvent event, String buttonID, Game game) {
        String factionOrColor = buttonID.substring(buttonID.indexOf('_') + 1);
        Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
        if (planetOwner == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve that player.");
            return;
        }
        String voteMessage = "Choosing to vote for one of " + factionOrColor
                + "'s planets. Please use the buttons to choose the planet you wish to vote for.";
        List<Button> outcomeActionRow = getPlanetOutcomeButtons(planetOwner, game, "outcome", null);
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
     * The fog-safe candidate list for an Elect Planet outcome, paginated to Discord's 25-buttons-per-message
     * cap.
     *
     * <p>Two sources: planets the voter could know exist, and planets already named in this agenda's votes.
     * The second set matters for correctness rather than secrecy - the vote summary shows later voters which
     * outcomes have been picked, so those must remain selectable even if the voter has never seen the planet.
     *
     * <p>Safe to relabel: an outcome is keyed by the raw planet id, and the summary renders it through
     * {@code getAgendaOutcomeName}, so nothing downstream depends on this button's text.
     *
     * <p>Known planets are not naturally bounded - stat-visibility alone can add a whole ally's holdings on
     * top of everything scouted, so a big pod with a couple of alliances routinely exceeds 25. Below that, this
     * returns the same flat list it always did; {@link #votePlanetPage} is the page-2-and-beyond entry point.
     */
    static List<Button> fogPlanetOutcomeButtons(Game game, Player player, String prefix) {
        List<Button> all = rawFogPlanetOutcomeButtons(game, player, prefix);
        return NewStuffHelper.buttonPagination(all, null, votePagePrefix(prefix), 25, 0, false);
    }

    private static List<Button> rawFogPlanetOutcomeButtons(Game game, Player player, String prefix) {
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

    private static final String VOTE_PLANET_PAGE_PREFIX = "votePlanetPage_";

    /**
     * Prefix used to build this outcome's page-nav button ids: {@code votePlanetPage_<realPrefix>|page<N>}. A
     * dedicated prefix - never {@code prefix} itself - so a nav press cannot be misrouted by
     * {@code @ButtonHandler}'s longest-prefix match into {@code outcome_}'s real vote-casting handler. Neither
     * real prefix ("outcome", "resolveAgendaVote_outcomeTie*") contains "|page", so the split below is exact.
     */
    private static String votePagePrefix(String realPrefix) {
        return VOTE_PLANET_PAGE_PREFIX + realPrefix + "|";
    }

    /** A page-nav id's decoded parts: which outcome prefix it was built for, and which page it names. */
    record ParsedPageID(String realPrefix, int page) {}

    /** Decodes a {@code votePlanetPage_<realPrefix>|page<N>} id, or null if it doesn't match that shape. */
    static ParsedPageID parseVotePageID(String pageButtonID) {
        String remainder = pageButtonID.substring(VOTE_PLANET_PAGE_PREFIX.length());
        Matcher pageMatch = Pattern.compile(RegexHelper.pageRegex()).matcher(remainder);
        if (!pageMatch.find()) return null;
        String realPrefix = StringUtils.substringBeforeLast(remainder, "|page");
        return new ParsedPageID(realPrefix, Integer.parseInt(pageMatch.group("page")));
    }

    /** The requested page of {@link #fogPlanetOutcomeButtons}'s full list. */
    static List<Button> votePlanetPageButtons(Game game, Player player, ParsedPageID parsed) {
        List<Button> all = rawFogPlanetOutcomeButtons(game, player, parsed.realPrefix());
        return NewStuffHelper.buttonPagination(
                all, null, votePagePrefix(parsed.realPrefix()), 25, parsed.page(), false);
    }

    /** Page 2 and beyond of {@link #fogPlanetOutcomeButtons}. Read-only: paging casts no vote. */
    @ButtonHandler(value = VOTE_PLANET_PAGE_PREFIX, save = false)
    static void votePlanetPage(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve that player.");
            return;
        }
        ParsedPageID parsed = parseVotePageID(buttonID);
        if (parsed == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> pageButtons = votePlanetPageButtons(game, player, parsed);
        String voteMessage = parsed.realPrefix().contains("outcomeTie")
                ? "As Speaker, please decide a winner."
                : "Please choose the planet you wish to vote for.";
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(), voteMessage + " (page " + (parsed.page() + 1) + ")", pageButtons);
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
