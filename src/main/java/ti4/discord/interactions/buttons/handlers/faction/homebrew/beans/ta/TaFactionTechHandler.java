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
import ti4.service.unit.UnitQueryService;

@UtilityClass
public class TaFactionTechHandler {

    private static final String YELLOW_TECH = "betaqr";
    private static final String GREEN_TECH = "betaro";
    private static final String QR_CHOOSE_PLANET = "taQRPlanet_";
    private static final String QR_CHOOSE_STRUCTURE = "taQRStructure_";
    private static final String QR_DECLINE = "taQRDecline";
    private static final String RO_PLANET_READY = "taROPlanetReady_";
    private static final String RO_PLANET_EXHAUST = "taROPlanetExhaust_";
    private static final String RO_DECLINE = "taRODecline";

    public static void resolveQuantumRestructuring(GenericInteractionCreateEvent event, Game game, Player player) {
        if (player == null || game == null || !player.hasTech(YELLOW_TECH)) {
            return;
        }

        List<String> designPlanets = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Tile tile = game.getTileContainingPlanet(planetName);
            if (tile == null) {
                continue;
            }

            if (TaAbilityHandler.planetHasAnyDesignAttached(tile, planetName)) {
                designPlanets.add(planetName);
            }
        }

        if (designPlanets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "There are no eligible planets with a design on them");
            ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String planetName : designPlanets) {
            Tile tile = game.getTileContainingPlanet(planetName);
            Planet planet = tile.getPlanet(planetName);
            if (tile == null || planet == null) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + QR_CHOOSE_PLANET + tile.getPosition() + "|" + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + QR_DECLINE, "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose a planet you control with a design for _Quantum Restructuring_.",
                buttons);
    }

    @ButtonHandler(QR_CHOOSE_PLANET)
    public static void resolveQrPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !buttonID.startsWith(QR_CHOOSE_PLANET)) {
            return;
        }

        String payload = buttonID.substring(QR_CHOOSE_PLANET.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String tilePosition = parts[0];
        String planetName = parts[1];

        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Planet planet = tile.getPlanet(planetName);
        if (planet == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!player.hasTech(YELLOW_TECH)
                || !player.canUsePlanet(planetName)
                || !TaAbilityHandler.planetHasAnyDesignAttached(tile, planetName)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + QR_CHOOSE_STRUCTURE + tilePosition + "|" + planetName + "|pds",
                "Place 1 PDS"));
        buttons.add(Buttons.green(
                player.factionButtonChecker() + QR_CHOOSE_STRUCTURE + tilePosition + "|" + planetName + "|spacedock",
                "Place 1 Spacedock"));
        buttons.add(Buttons.red(player.factionButtonChecker() + QR_DECLINE, "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose which structure to place on "
                        + Helper.getPlanetRepresentation(planetName, game),
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(QR_CHOOSE_STRUCTURE)
    public static void resolveQrStructure(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !buttonID.startsWith(QR_CHOOSE_STRUCTURE)) {
            return;
        }

        String payload = buttonID.substring(QR_CHOOSE_STRUCTURE.length());
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tilePosition = parts[0];
        String planetName = parts[1];
        String structureType = parts[2];

        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null
                || !player.hasTech(YELLOW_TECH)
                || !player.canUsePlanet(planetName)
                || !TaAbilityHandler.planetHasAnyDesignAttached(tile, planetName)
                || (!"pds".equals(structureType) && (!"spacedock".equals(structureType)))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int pdsRemaining = player.getUnitCap("pd") - UnitQueryService.countUnits(game, player, "pds", false);

        int sdRemaining = player.getUnitCap("sd") - UnitQueryService.countUnits(game, player, "spacedock", false);

        if ("pds".equals(structureType) && pdsRemaining < 1) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + ", you have no PDS remaining in your reinforcements.");
            ButtonHelper.deleteMessage(event);
            ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
            return;
        }

        if ("spacedock".equals(structureType) && sdRemaining < 1) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + ", you have no more Space Docks in your reinforcements.");
            ButtonHelper.deleteMessage(event);
            ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
            return;
        }

        String unitString = "pds".equals(structureType) ? "1 pds " + planetName : "1 spacedock " + planetName;
        AddUnitService.addUnits(event, tile, game, player.getColor(), unitString);

        ButtonHelper.deleteMessage(event);

        String structureName = "pds".equalsIgnoreCase(structureType) ? "PDS" : "space dock";
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " placed 1 " + structureName + " on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " using _Quantum Restructuring_.");
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }

    @ButtonHandler(QR_DECLINE)
    public static void resolveQrDecline(ButtonInteractionEvent event, Game game, Player player) {
        if (event == null || game == null || player == null || !player.hasTech(YELLOW_TECH)) {
            return;
        }

        ButtonHelper.deleteMessage(event);
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
            Planet planet = game.getPlanet(planetName);
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
                player.toString() + ", please choose a planet to exhaust for _Resource Optimization_.",
                buttons);
    }

    @ButtonHandler(RO_PLANET_EXHAUST)
    private static void resolveRoExhaust(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasTech(GREEN_TECH)) {
            return;
        }

        String costPlanet = buttonID.substring(RO_PLANET_EXHAUST.length());
        if (!player.containsPlanet(costPlanet) || !player.hasPlanetReady(costPlanet)) {
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
            Planet planet = game.getPlanet(planetName);
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
                player.toString() + ", please choose a planet to ready using _Resource Optimization_.",
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
        if (!player.containsPlanet(targetPlanet) || !player.isPlanetExhausted(targetPlanet)) {
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
                player.toString()
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
