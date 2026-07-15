package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kairn;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class KairnTechHandler {
    private static final String SURVEYORS_LENS = "thkairny";
    private static final String EXHAUST_LENS = "kairnExhaustLens";
    private static final String SURVEY_PLANET = "surveyTargetPlanet_";
    private static final String LENS_TRAIT = "exploreLensTrait_";
    private static final String READY_LENS = "kairnReadyLens_";
    private static final String DECLINE_READY_LENS = "kairnDeclineReadyLens_";
    private static final String FRAGMENT_WINDOW = "kairnLensFragment_";
    private static final Set<String> EXPLORATION_TRAITS =
            Set.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL);

    // Surveyor's Lens
    public static Button getSurveyorsLensButton(Player player) {
        return Buttons.green(
                player.factionButtonChecker() + EXHAUST_LENS, "Exhaust Surveyor's Lens", FactionEmojis.kairn);
    }

    @ButtonHandler(EXHAUST_LENS)
    public static void resolveLensExhaust(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasTechReady(SURVEYORS_LENS)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile activeTile = game.getTileByPosition(game.getActiveSystem());
        if (activeTile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There is no active system to explore.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Planet planet : activeTile.getPlanetUnitHolders()) {
            if (planet.getPlanetTypes().stream().anyMatch(EXPLORATION_TRAITS::contains)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + SURVEY_PLANET + planet.getName(),
                        "Explore " + planet.getRepresentation(game)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There are no explorable planets in the active system.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.removeStoredValue(FRAGMENT_WINDOW + player.getFaction());
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", choose which planet to explore using _Surveyor's Lens_:",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SURVEY_PLANET)
    public static void resolveExploreSurveyPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String planetName = buttonID.replace(SURVEY_PLANET, "");
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (!player.hasTechReady(SURVEYORS_LENS)
                || tile == null
                || planet == null
                || !tile.getPosition().equals(game.getActiveSystem())) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That planet is no longer eligible for Surveyor's Lens.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String trait : planet.getPlanetTypes()) {
            if (!EXPLORATION_TRAITS.contains(trait)) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + LENS_TRAIT + trait + "|" + planetName,
                    "Explore as " + StringUtils.capitalize(trait),
                    ExploreEmojis.getTraitEmoji(trait)));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "That planet has no explorable trait.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", choose which trait to explore " + planet.getRepresentation(game)
                        + " as.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(LENS_TRAIT)
    public static void resolveLensTrait(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String[] payload = buttonID.substring(LENS_TRAIT.length()).split("\\|", 2);
        if (payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String trait = payload[0];
        String planetName = payload[1];
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (!player.hasTechReady(SURVEYORS_LENS)
                || tile == null
                || planet == null
                || !tile.getPosition().equals(game.getActiveSystem())
                || !EXPLORATION_TRAITS.contains(trait)
                || !planet.getPlanetTypes().contains(trait)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That exploration is no longer eligible for Surveyor's Lens.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String cardID = game.drawExplore(trait);
        if (cardID == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There are no cards left in that exploration deck.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.exhaustTech(SURVEYORS_LENS);
        ButtonHelper.deleteMessage(event);
        String message = player.getRepresentation() + " explored " + planet.getRepresentation(game)
                + " using _Surveyor's Lens_.";
        ExploreService.resolveExplore(event, cardID, tile, planetName, message, player, game);
    }

    public static void offerSurveyorsLensReady(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            String planetName,
            String cardID) {
        if (event == null
                || game == null
                || player == null
                || planetName == null
                || cardID == null
                || !player.hasTech(SURVEYORS_LENS)
                || player.hasTechReady(SURVEYORS_LENS)) {
            return;
        }

        Tile exploredTile = tile == null ? game.getTileFromPlanet(planetName) : tile;
        Planet exploredPlanet = game.getUnitHolderFromPlanet(planetName);
        if (exploredTile == null
                || exploredPlanet == null
                || exploredPlanet.getUnitCount(UnitType.Infantry, player) < 1) {
            return;
        }

        String window = cardID + "|" + planetName;
        game.setStoredValue(FRAGMENT_WINDOW + player.getFaction(), window);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", you gained a relic fragment. You may remove 1 infantry from "
                        + exploredPlanet.getRepresentation(game) + " to ready _Surveyor's Lens_.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + READY_LENS + window,
                                "Remove 1 Infantry to Ready Surveyor's Lens"),
                        Buttons.red(player.factionButtonChecker() + DECLINE_READY_LENS + window, "Decline")));
    }

    @ButtonHandler(READY_LENS)
    public static void readyLens(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String window = buttonID.substring(READY_LENS.length());
        String[] payload = window.split("\\|", 2);
        if (payload.length != 2 || !window.equals(game.getStoredValue(FRAGMENT_WINDOW + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String planetName = payload[1];
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (tile == null || planet == null || planet.getUnitCount(UnitType.Infantry, player) < 1) {
            game.removeStoredValue(FRAGMENT_WINDOW + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There is no longer an infantry on that planet to remove.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        int removed = RemoveUnitService.removeUnit(event, tile, game, player, planet, UnitType.Infantry, 1).stream()
                .mapToInt(RemoveUnitService.RemovedUnit::getTotalRemoved)
                .sum();
        game.removeStoredValue(FRAGMENT_WINDOW + player.getFaction());
        if (removed < 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There is no longer an infantry on that planet to remove.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.refreshTech(SURVEYORS_LENS);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " removed 1 infantry from " + planet.getRepresentation(game)
                        + " and readied _Surveyor's Lens_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DECLINE_READY_LENS)
    public static void declineReadyLens(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String window = buttonID.substring(DECLINE_READY_LENS.length());
        if (window.equals(game.getStoredValue(FRAGMENT_WINDOW + player.getFaction()))) {
            game.removeStoredValue(FRAGMENT_WINDOW + player.getFaction());
        }
        ButtonHelper.deleteMessage(event);
    }
}
