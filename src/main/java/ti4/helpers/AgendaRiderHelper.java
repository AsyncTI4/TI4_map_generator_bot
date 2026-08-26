package ti4.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.actioncards.theodisi.TransitRiderLLButtonHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.StrategyCardSetModel;
import ti4.model.TechnologyModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.fow.PlanetTargetService;
import ti4.service.tech.ListTechService;

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
            // getPlanetRepresentation appends live resources/influence and a [DMZ] marker, which is current
            // map state the readers of this announcement may not be entitled to. The agenda summary uses the
            // static planet name for exactly this reason, so match it.
            cleanedChoice = game.isFowMode()
                    ? AgendaHelper.getAgendaOutcomeName(game, choice, true)
                    : Helper.getPlanetRepresentation(choice, game);
        }
        String voteMessage = "chose to put a " + rider + " on \"" + StringUtils.capitalize(cleanedChoice) + "\".";
        voteMessage = !game.isFowMode()
                ? player.getRepresentationNoPing() + " " + voteMessage
                : StringUtils.capitalize(voteMessage);

        String identifier = game.isFowMode() ? player.getColor() : player.getFaction();

        if ("Transit Rider".equalsIgnoreCase(rider)) {
            TransitRiderLLButtonHandler.registerPrediction(game, player);
        }

        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        String existingData = outcomes.getOrDefault(choice, "empty");
        existingData = "empty".equalsIgnoreCase(existingData)
                ? identifier + "_" + rider
                : existingData + ";" + identifier + "_" + rider;
        game.setCurrentAgendaVote(choice, existingData);

        MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
        String summary = AgendaSummaryHelper.getSummaryOfVotes(game, true);
        // In fog the summary names the outcome, so pushing it to the main channel would broadcast a planet
        // that most players have no business knowing about. Voters get the summary in their own channel as
        // the turn passes to them (see AgendaHelper.resolvingAnAgendaVote), and everything becomes public at
        // resolution time anyway.
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), summary + "\n \n");
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", don't forget you now have to decide on whether you will play any more \"after\"s.");
    }

    /**
     * @param planetSource whose planets to list. In fog this is ignored in favour of the planets
     *                     {@code viewer} could know about - listing one player's holdings is the leak.
     * @param viewer       the player who will see these buttons.
     */
    private static List<Button> getPlanetOutcomeButtons(
            Player planetSource, Player viewer, Game game, String prefix, String rider) {
        List<Button> planetOutcomeButtons = new ArrayList<>();
        Collection<String> planets;
        if (game.isFowMode() && viewer != null) {
            // Rider ids are "<prefix>rider_planet;<planet>_<rider>", so the planet is not the trailing
            // segment and the Blind Target modal (which appends "_<target>") cannot express them. Riders are
            // therefore limited to planets the predictor knows about, which is still far tighter than the
            // previous behaviour of listing a chosen player's entire holdings.
            planets = new ArrayList<>(PlanetTargetService.knownPlanetIds(
                    game, viewer, new HashSet<>(game.getCurrentAgendaVotes().keySet())));
        } else if (planetSource == null) {
            return planetOutcomeButtons;
        } else {
            planets = new ArrayList<>(planetSource.getPlanets());
        }
        // Same labelling rule as the fog candidate lists, computed once. The fog set here includes planets
        // the viewer merely remembers and outcomes they have never seen, so live resources/influence and the
        // [DMZ] marker would report state changed since they last looked.
        var fogSafeLabel = PlanetTargetService.fogSafeLabeller(game, viewer);
        for (String planet : planets) {
            if (game.getTileFromPlanet(planet) == null) continue;
            String label = fogSafeLabel.apply(planet);
            Button button = rider == null
                    ? Buttons.gray(prefix + "_" + planet, label)
                    : Buttons.gray(prefix + "rider_planet;" + planet + "_" + rider, label);
            planetOutcomeButtons.add(button);
        }
        planetOutcomeButtons.sort((a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        return planetOutcomeButtons;
    }

    /**
     * {@link #getPlanetOutcomeButtons}, paginated past Discord's 25-button cap in fog. Not routed through
     * {@link PlanetTargetService#targetButtons} - this flow's ids are shaped
     * {@code prefix + "rider_planet;" + planet + "_" + rider}, not the {@code prefix + "_" + planetId} shape
     * that service hardcodes, so it gets its own small local wrapper instead, the same way the agenda vote
     * list's pagination worked before it was generalized into that service.
     */
    static List<Button> paginatedPlanetOutcomeButtons(
            Player planetSource, Player viewer, Game game, String prefix, String rider, String navPrefix, int page) {
        List<Button> all = getPlanetOutcomeButtons(planetSource, viewer, game, prefix, rider);
        if (!game.isFowMode()) {
            return all;
        }
        return NewStuffHelper.buttonPagination(all, null, navPrefix, 25, page, false);
    }

    public static List<Button> getAgendaButtons(String riderName, Game game, String prefix) {
        String agendaDetails = game.getCurrentAgendaInfo().split("_")[1];
        String lower = agendaDetails.toLowerCase();

        if (agendaDetails.contains("For")) {
            return getForAgainstOutcomeButtons(
                    game, riderName, prefix, game.getCurrentAgendaInfo().split("_")[2], null);
        }
        if (lower.contains("player")) {
            return getPlayerOutcomeButtons(game, riderName, prefix, null);
        }
        if (lower.contains("planet")) {
            return riderName == null
                    ? getPlayerOutcomeButtons(game, null, "tiedPlanets_" + prefix, "planetRider")
                    : getPlayerOutcomeButtons(game, riderName, prefix, "planetRider");
        }
        if (lower.contains("secret")) {
            return getSecretOutcomeButtons(game, riderName, prefix);
        }
        if (lower.contains("strategy")) {
            return getStrategyOutcomeButtons(game, riderName, prefix);
        }
        if (agendaDetails.contains("unit upgrade")) {
            return getUnitUpgradeOutcomeButtons(game, riderName, prefix);
        }
        if (lower.contains("unit")) {
            return getUnitOutcomeButtons(game, riderName, prefix);
        }
        return getLawOutcomeButtons(game, riderName, prefix);
    }

    @ButtonHandler("planetRider_")
    public static void planetRider(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        buttonID = buttonID.replace("planetRider_", "");
        // A page-nav press re-enters this same handler (see paginatedPlanetOutcomeButtons's navPrefix) with
        // a trailing "pageN". Stripping it first, before the color/rider split below, means a fresh press
        // and a page-N press reduce to the identical "color_rider" string either way.
        int page = 0;
        Matcher pageMatch = Pattern.compile(RegexHelper.pageRegex()).matcher(buttonID);
        if (pageMatch.find()) {
            page = Integer.parseInt(pageMatch.group("page"));
            buttonID = buttonID.substring(0, pageMatch.start());
        }
        String factionOrColor = buttonID.substring(0, buttonID.indexOf('_'));
        Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
        String voteMessage = "Chose to Rider for one of " + factionOrColor + "'s planets. Please choose which one.";

        String rider = buttonID.replace(factionOrColor + "_", "");
        // Reuses the presser's own gate, matching the real target buttons built one line below - only the
        // player who opened this list can page through it.
        String navPrefix = player.factionButtonChecker() + "planetRider_" + factionOrColor + "_" + rider;
        List<Button> outcomeActionRow = paginatedPlanetOutcomeButtons(
                planetOwner, player, game, player.factionButtonChecker(), rider, navPrefix, page);
        if (game.isFowMode()) {
            voteMessage = "Chose to Rider for a planet. Please choose which one.";
        }
        if (page > 0) {
            voteMessage += " (page " + (page + 1) + ")";
        }

        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getLawOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> lawButtons = new ArrayList<>();
        for (Map.Entry<String, Integer> law : game.getLaws().entrySet()) {
            String lawName = Mapper.getAgendaTitleNoCap(law.getKey());
            Button button;
            if (rider == null) {
                button = Buttons.blue(prefix + "_" + law.getKey(), lawName);
            } else {
                button = Buttons.blue(prefix + "rider_law;" + law.getKey() + "_" + rider, lawName);
            }
            lawButtons.add(button);
        }
        return lawButtons;
    }

    public static List<Button> getForAgainstOutcomeButtons(
            Game game, String rider, String prefix, String agendaID, Player player) {
        List<Button> voteButtons = new ArrayList<>();
        Button buttonFor;
        Button buttonAgainst;
        String factionChecker = "";
        if (player != null) {
            factionChecker = player.factionButtonChecker();
        }
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        Integer agendaInt = null;
        String forEmojiString = "👍";
        String againstEmojiString = "👎";
        try {
            agendaInt = Integer.valueOf(agendaID);
        } catch (NumberFormatException e) {
        }
        if (agendaInt != null) {
            String agendaAlias = "";
            for (Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
                if (agendas.getValue().equals(agendaInt)) {
                    agendaAlias = agendas.getKey();
                    break;
                }
            }
            AgendaModel agendaDetails = Mapper.getAgenda(agendaAlias);
            if (agendaDetails != null) {
                forEmojiString = agendaDetails.getForEmoji();
                againstEmojiString = agendaDetails.getAgainstEmoji();
            }
            for (TI4Emoji emoji : TI4Emoji.allEmojiEnums()) {
                if (forEmojiString.equals(emoji.name())) {
                    forEmojiString = emoji.toString();
                    break;
                }
            }
            for (TI4Emoji emoji : TI4Emoji.allEmojiEnums()) {
                if (againstEmojiString.equals(emoji.name())) {
                    againstEmojiString = emoji.toString();
                    break;
                }
            }
        }
        if (rider == null) {
            buttonFor = Buttons.green(factionChecker + prefix + "_for", "For");
            buttonAgainst = Buttons.red(factionChecker + prefix + "_against", "Against");
        } else {
            buttonFor = Buttons.green(factionChecker + prefix + "rider_fa;for_" + rider, "For");
            buttonAgainst = Buttons.red(factionChecker + prefix + "rider_fa;against_" + rider, "Against");
        }

        buttonFor = buttonFor.withEmoji(Emoji.fromFormatted(forEmojiString));
        buttonAgainst = buttonAgainst.withEmoji(Emoji.fromFormatted(againstEmojiString));

        voteButtons.add(buttonFor);
        voteButtons.add(buttonAgainst);
        return voteButtons;
    }

    public static List<Button> getSecretOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> secretButtons = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            for (Map.Entry<String, Integer> so : player.getSecretsScored().entrySet()) {
                Button button;
                String soName = Mapper.getSecretObjectivesJustNames().get(so.getKey());
                if (rider == null) {
                    button = Buttons.blue(prefix + "_" + so.getKey(), soName);
                } else {
                    button = Buttons.blue(prefix + "rider_so;" + so.getKey() + "_" + rider, soName);
                }
                if (!game.isFowMode()) {
                    String colorEmojiString =
                            ColorEmojis.getColorEmoji(player.getColor()).toString();
                    button = button.withEmoji(Emoji.fromFormatted(colorEmojiString));
                }
                secretButtons.add(button);
            }
        }
        return secretButtons;
    }

    public static List<Button> getUnitUpgradeOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> buttons = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            for (TechnologyModel tech : ListTechService.getAllNonFactionUnitUpgradeTech(game, player)) {
                Button button;
                if (rider == null) {
                    button = Buttons.blue(prefix + "_" + tech.getAlias(), tech.getName());
                } else {
                    button = Buttons.blue(prefix + "rider_so;" + tech.getAlias() + "_" + rider, tech.getName());
                }
                buttons.add(button);
            }
        }
        return buttons;
    }

    public static List<Button> getUnitOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> buttons = new ArrayList<>();
        for (TechnologyModel tech : ListTechService.getAllNonFactionUnitUpgradeTech(game)) {
            Button button;
            if (rider == null) {
                button = Buttons.blue(prefix + "_" + tech.getAlias(), tech.getName());
            } else {
                button = Buttons.blue(prefix + "rider_so;" + tech.getAlias() + "_" + rider, tech.getName());
            }
            buttons.add(button);
        }
        return buttons;
    }

    public static List<Button> getStrategyOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> strategyButtons = new ArrayList<>();
        StrategyCardSetModel stratCards = game.getStrategyCardSet();
        for (ti4.model.StrategyCardModel sc : stratCards.getStrategyCardModels()) {
            Button button;
            TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(sc.getInitiative());
            if (rider == null) {
                button = Buttons.blue(
                        prefix + "_" + sc.getInitiative(), stratCards.getSCName(sc.getInitiative()), scEmoji);
            } else {
                button = Buttons.blue(
                        prefix + "rider_sc;" + sc.getInitiative() + "_" + rider,
                        stratCards.getSCName(sc.getInitiative()),
                        scEmoji);
            }
            strategyButtons.add(button);
        }
        return strategyButtons;
    }

    public static List<Button> getPlayerOutcomeButtons(Game game, String rider, String prefix, String planetRes) {
        List<Button> playerOutcomeButtons = new ArrayList<>();
        List<Player> players = game.getRealPlayers();
        if (prefix.contains("solagent") || prefix.contains("letnevagent")) {
            players = game.getRealPlayersNNeutral();
        }
        if (prefix.contains("yinHero")) {
            players = game.getRealPlayersNNeutral();
        }
        for (Player player : players) {
            String faction = player.getFaction();
            Button button;
            if (!game.isFowMode() && !faction.contains("franken")) {
                if (rider != null) {
                    if (planetRes != null) {
                        button = Buttons.blue(
                                prefix + planetRes + "_" + faction + "_" + rider, StringUtils.capitalize(faction));
                    } else {
                        button = Buttons.blue(
                                prefix + "rider_player;" + faction + "_" + rider, StringUtils.capitalize(faction));
                    }
                } else {
                    button = Buttons.blue(prefix + "_" + faction, StringUtils.capitalize(faction));
                }
                String colorEmojiString =
                        ColorEmojis.getColorEmoji(player.getColor()).toString();
                button = button.withEmoji(Emoji.fromFormatted(colorEmojiString));
            } else {
                if (rider != null) {
                    if (planetRes != null) {
                        // Keep the prefix: it carries the FFCC_<faction>_ ownership check, and dropping it
                        // let any player press another player's rider-target button.
                        button = Buttons.blue(
                                prefix + planetRes + "_" + player.getColor() + "_" + rider, player.getColor());
                    } else {
                        button = Buttons.blue(
                                prefix + "rider_player;" + player.getColor() + "_" + rider, player.getColor());
                    }
                } else {
                    button = Buttons.blue(prefix + "_" + player.getColor(), player.getColor());
                }
            }
            playerOutcomeButtons.add(button);
        }
        return playerOutcomeButtons;
    }
}
