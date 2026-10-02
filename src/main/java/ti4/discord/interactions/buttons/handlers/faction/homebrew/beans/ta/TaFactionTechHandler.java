package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.planet.PlanetService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class TaFactionTechHandler {

    private static final String YELLOW_TECH = "betaqr";
    private static final String QR_PLACE_PDS = "taQRPlacePds_";
    private static final String GREEN_TECH = "betaro";
    private static final String RO_PLANET_READY = "taROPlanetReady_";
    private static final String RO_PLANET_EXHAUST = "taROPlanetExhaust_";
    private static final String RO_DECLINE = "taRODecline";

    public static void resolveQuantumRestructuring(GenericInteractionCreateEvent event, Game game, Player player) {
        if (event == null || game == null || player == null || !player.hasTech(YELLOW_TECH)) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Tile tile = game.getTileFromPlanet(planetName);
            Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(planetName);
            if (planet != null && TaAbilityHandler.planetHasAnyAttachment(tile, planetName)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + QR_PLACE_PDS + tile.getPosition() + "|" + planetName,
                        "Place PDS on " + Helper.getPlanetRepresentation(planetName, game)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " has no controlled planet with an attachment for _Quantum Restructuring_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the planet on which to place 1 PDS with _Quantum Restructuring_.",
                buttons);
    }

    @ButtonHandler(QR_PLACE_PDS)
    private static void resolveQuantumRestructuringPlacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(QR_PLACE_PDS.length()).split("\\|", 2);
        if (game == null || player == null || parts.length != 2 || !player.hasTech(YELLOW_TECH)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = game.getTileByPosition(parts[0]);
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(parts[1]);
        if (planet == null
                || !player.getPlanets().contains(parts[1])
                || !TaAbilityHandler.planetHasAnyAttachment(tile, parts[1])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 pds " + parts[1]);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 PDS on " + Helper.getPlanetRepresentation(parts[1], game)
                        + " with _Quantum Restructuring_.");
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }

    public static void resolveResOp(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasTech(GREEN_TECH)) {
            return;
        }

        List<String> costPlanets = new ArrayList<>(player.getReadiedPlanets());
        List<String> targetPlanets = new ArrayList<>(player.getExhaustedPlanets());
        if (costPlanets.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "There are no readied planets available.");
            return;
        }
        if (targetPlanets.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "There are no exhausted planets to ready.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String planetName : costPlanets) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null) {
                ButtonHelper.deleteMessage(event);
                return;
            }

            buttons.add(Buttons.red(
                    player.factionButtonChecker() + RO_PLANET_EXHAUST + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + RO_DECLINE, "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose a planet to exhaust for _Resource Optimization_.",
                buttons);
    }

    @ButtonHandler(RO_PLANET_EXHAUST)
    private static void resolveRoExhaust(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasTech(GREEN_TECH)) {
            return;
        }

        String costPlanet = buttonID.substring(RO_PLANET_EXHAUST.length());
        if (!player.getPlanets().contains(costPlanet)
                || !player.getReadiedPlanets().contains(costPlanet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.exhaustPlanet(costPlanet);
        ButtonHelper.deleteMessage(event);

        List<String> targetPlanets = new ArrayList<>(player.getExhaustedPlanets());
        targetPlanets.remove(costPlanet);
        if (targetPlanets.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String planetName : targetPlanets) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null) {
                ButtonHelper.deleteMessage(event);
                return;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + RO_PLANET_READY + costPlanet + "|" + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose a planet to ready using _Resource Optimization_.",
                buttons);
    }

    @ButtonHandler(RO_PLANET_READY)
    private static void resolveRoReady(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !buttonID.startsWith(RO_PLANET_READY)) {
            return;
        }

        String payload = buttonID.substring(RO_PLANET_READY.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String costPlanet = parts[0];
        String targetPlanet = parts[1];
        if (!player.getPlanets().contains(targetPlanet)
                || !player.getExhaustedPlanets().contains(targetPlanet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String readiedAbility = (player.getExhaustedPlanetsAbilities().contains(targetPlanet)
                ? " They readied that planet's ability card as well."
                : "");

        PlanetService.refreshPlanet(player, targetPlanet);
        ButtonHelper.deleteMessage(event);
        if (player.getExhaustedPlanetsAbilities().contains(targetPlanet)) {
            player.refreshPlanetAbility(targetPlanet);
        }

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " exhausted " + Helper.getPlanetRepresentation(costPlanet, game)
                        + " to ready " + Helper.getPlanetRepresentation(targetPlanet, game)
                        + " using _Resource Optimization_."
                        + readiedAbility);
    }

    @ButtonHandler(RO_DECLINE)
    private static void resolveRoDecline(ButtonInteractionEvent event, Game game, Player player) {
        if (event == null || game == null || player == null || !player.hasTech(GREEN_TECH)) {
            return;
        }

        ButtonHelper.deleteMessage(event);
    }
}
