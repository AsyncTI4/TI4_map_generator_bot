package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.AttachmentModel;
import ti4.service.planet.PlanetService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class TaBreakthroughHandler {
    private static final String ADAPTIVE_ECONOMY = "tabt";
    private static final String AE_EXHAUST = "taAeExhaust";
    private static final String AE_STRAT = "taAeStrat";
    private static final String AE_SOURCE_PREFIX = "taAeSource_";
    private static final String AE_TARGET_PREFIX = "taAeTarget_";
    private static final List<String> MOVABLE_DESIGNS =
            List.of("designunify", "designtranspose", "designprestige", "designabundance");
    private static final String EXHAUST_COST = "exhaust";
    private static final String STRAT_COST = "strat";
    private static final String OFFER_ADAPTIVE_ECONOMY = "offerAdaptiveEconomy";

    public static boolean hasAdaptiveEconomyTargets(Player player, Game game) {
        if (player == null || game == null || !player.hasUnlockedBreakthrough(ADAPTIVE_ECONOMY)) {
            return false;
        }

        for (String sourcePlanet : player.getPlanets()) {
            Tile sourceTile = game.getTileContainingPlanet(sourcePlanet);
            if (sourceTile == null) {
                continue;
            }

            if (!planetHasMovableDesign(sourceTile, sourcePlanet)) {
                continue;
            }

            if (!getAdaptiveEconomyTargets(player, game, sourcePlanet).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean planetHasMovableDesign(Tile tile, String planetName) {
        return getMovableDesignToken(tile, planetName) != null;
    }

    private static String getMovableDesignName(Tile tile, String planetName) {
        String token = getMovableDesignToken(tile, planetName);
        if (token == null) {
            return null;
        }

        AttachmentModel attachment = Mapper.getAttachmentInfo(token);
        if (attachment == null) {
            return null;
        }

        return attachment.getName().replace(" (Design)", "");
    }

    private static String getMovableDesignToken(Tile tile, String planetName) {
        if (tile == null || planetName == null || planetName.isBlank()) {
            return null;
        }

        Planet planet = tile.getPlanet(planetName);
        if (planet == null) {
            return null;
        }

        for (String token : planet.getTokenList()) {
            AttachmentModel attachment = Mapper.getAttachmentInfo(token);
            if (attachment == null) {
                continue;
            }

            if (MOVABLE_DESIGNS.contains(attachment.getAlias())) {
                return token;
            }
        }

        return null;
    }

    private static List<String> getAdaptiveEconomyTargets(Player player, Game game, String sourcePlanet) {
        List<String> targets = new ArrayList<>();
        if (player == null || game == null || sourcePlanet == null || sourcePlanet.isBlank()) {
            return targets;
        }

        Tile sourceTile = game.getTileContainingPlanet(sourcePlanet);
        if (sourceTile == null) {
            return targets;
        }

        Set<String> adjacentPositions = FoWHelper.getAdjacentTiles(game, sourceTile.getPosition(), player, false, true);
        for (String pos : adjacentPositions) {
            if (sourceTile.getPosition().equals(pos)) {
                continue;
            }

            Tile adjacentTile = game.getTileByPosition(pos);
            if (adjacentTile == null) {
                continue;
            }

            for (Planet planet : adjacentTile.getPlanetUnitHolders()) {
                String planetName = planet.getName();
                if (sourcePlanet.equals(planetName)) {
                    continue;
                }
                if (!player.containsPlanet(planetName)) {
                    continue;
                }
                if (TaAbilityHandler.planetHasAnyDesignAttached(adjacentTile, planetName)) {
                    continue;
                }

                targets.add(planetName);
            }
        }

        return targets;
    }

    public static void offerAdaptiveEconomyButtons(Player player, Game game) {
        if (player == null || game == null) {
            return;
        }
        if (!player.hasBreakthrough(ADAPTIVE_ECONOMY)) {
            return;
        }
        if (!hasAdaptiveEconomyTargets(player, game)) {
            return;
        }

        List<Button> buttons = new ArrayList<>();

        if (player.hasReadyBreakthrough(ADAPTIVE_ECONOMY)) {
            buttons.add(Buttons.green(player.factionButtonChecker() + AE_EXHAUST, "Exhaust Adaptive Economy"));
        }

        if (player.getStrategicCC() > 0) {
            buttons.add(Buttons.blue(player.factionButtonChecker() + AE_STRAT, "Spend 1 Strategy Token"));
        }

        if (buttons.isEmpty()) {
            return;
        }

        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.toString()
                        + ", you may use _Adaptive Economy_ to move 1 design to an adjacent planet you control, then ready that planet.",
                buttons);
    }

    @ButtonHandler(AE_EXHAUST)
    public static void resolveAdaptiveEconomyExhaust(ButtonInteractionEvent event, Player player, Game game) {
        if (event == null || player == null || game == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasUnlockedBreakthrough(ADAPTIVE_ECONOMY)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        offerAdaptiveEconomySourceButtons(event, player, game, EXHAUST_COST);
    }

    @ButtonHandler(AE_STRAT)
    public static void resolveAdaptiveEconomyStrat(ButtonInteractionEvent event, Player player, Game game) {
        if (event == null || player == null || game == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasUnlockedBreakthrough(ADAPTIVE_ECONOMY)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        offerAdaptiveEconomySourceButtons(event, player, game, STRAT_COST);
    }

    private static void offerAdaptiveEconomySourceButtons(
            ButtonInteractionEvent event, Player player, Game game, String costType) {
        if (event == null || player == null || game == null || costType == null || costType.isBlank()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasUnlockedBreakthrough(ADAPTIVE_ECONOMY)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();

        for (String sourcePlanet : player.getPlanets()) {
            Tile sourceTile = game.getTileContainingPlanet(sourcePlanet);
            if (sourceTile == null) {
                continue;
            }
            if (!planetHasMovableDesign(sourceTile, sourcePlanet)) {
                continue;
            }
            if (getAdaptiveEconomyTargets(player, game, sourcePlanet).isEmpty()) {
                continue;
            }

            String designName = getMovableDesignName(sourceTile, sourcePlanet);
            if (designName == null) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + AE_SOURCE_PREFIX + costType + "|" + sourcePlanet,
                    "Move " + designName + " from " + Helper.getPlanetRepresentationNoResInf(sourcePlanet, game)));
        }

        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), player.toString() + ", choose a planet to move a design from.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(AE_SOURCE_PREFIX)
    public static void resolveAdaptiveEconomySource(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null || !buttonID.startsWith(AE_SOURCE_PREFIX)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasUnlockedBreakthrough(ADAPTIVE_ECONOMY)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring(AE_SOURCE_PREFIX.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String costType = parts[0];
        String sourcePlanet = parts[1];

        if (!EXHAUST_COST.equals(costType) && !STRAT_COST.equals(costType)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile sourceTile = game.getTileContainingPlanet(sourcePlanet);
        if (sourceTile == null || !player.containsPlanet(sourcePlanet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!planetHasMovableDesign(sourceTile, sourcePlanet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        offerAdaptiveEconomyTargetButtons(event, player, game, costType, sourcePlanet);
    }

    public static void offerAdaptiveEconomyTargetButtons(
            ButtonInteractionEvent event, Player player, Game game, String costType, String sourcePlanet) {
        if (event == null || player == null || game == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<String> targets = getAdaptiveEconomyTargets(player, game, sourcePlanet);
        if (targets.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String targetPlanet : targets) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker()
                            + AE_TARGET_PREFIX
                            + costType + "|" + sourcePlanet + "|" + targetPlanet,
                    "Move to " + Helper.getPlanetRepresentationNoResInf(targetPlanet, game)));
        }

        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.toString()
                        + ", choose a planet to move the design from "
                        + Helper.getPlanetRepresentation(sourcePlanet, game)
                        + " to.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(AE_TARGET_PREFIX)
    public static void resolveAdaptiveEconomyTarget(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null || !buttonID.startsWith(AE_TARGET_PREFIX)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasUnlockedBreakthrough(ADAPTIVE_ECONOMY)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring(AE_TARGET_PREFIX.length());
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String costType = parts[0];
        String sourcePlanet = parts[1];
        String targetPlanet = parts[2];

        if (!EXHAUST_COST.equals(costType) && !STRAT_COST.equals(costType)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile sourceTile = game.getTileContainingPlanet(sourcePlanet);
        Tile targetTile = game.getTileContainingPlanet(targetPlanet);
        if (sourceTile == null || targetTile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!player.containsPlanet(sourcePlanet)
                || !player.containsPlanet(targetPlanet)
                || sourcePlanet.equals(targetPlanet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String designToken = getMovableDesignToken(sourceTile, sourcePlanet);
        if (designToken == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<String> legalTargets = getAdaptiveEconomyTargets(player, game, sourcePlanet);
        if (!legalTargets.contains(targetPlanet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!payAdaptiveEconomyCost(player, game, costType)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        sourceTile.removeToken(designToken, sourcePlanet);
        targetTile.addToken(designToken, targetPlanet);
        AddUnitService.addUnits(event, targetTile, game, player.getColor(), "2 infantry " + targetPlanet);
        PlanetService.refreshPlanet(player, targetPlanet);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.toString()
                        + " used _Adaptive Economy_ to move a design from "
                        + Helper.getPlanetRepresentation(sourcePlanet, game)
                        + " to "
                        + Helper.getPlanetRepresentation(targetPlanet, game)
                        + ", then placed 2 infantry on it and readied it.");

        ButtonHelper.deleteMessage(event);
    }

    private static boolean payAdaptiveEconomyCost(Player player, Game game, String costType) {
        if (player == null || game == null || costType == null) {
            return false;
        }

        if (EXHAUST_COST.equals(costType)) {
            if (!player.hasReadyBreakthrough(ADAPTIVE_ECONOMY)) {
                return false;
            }
            BreakthroughCommandHelper.exhaustBreakthrough(player, ADAPTIVE_ECONOMY);
            return true;
        }

        if (STRAT_COST.equals(costType)) {
            if (player.getStrategicCC() < 1) {
                return false;
            }
            player.setStrategicCC(player.getStrategicCC() - 1);
            return true;
        }

        return false;
    }

    @ButtonHandler(OFFER_ADAPTIVE_ECONOMY)
    public static void resolveOfferAdaptiveEconomy(ButtonInteractionEvent event, Player player, Game game) {
        if (event == null || player == null || game == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasReadyBreakthrough(ADAPTIVE_ECONOMY) && player.getStrategicCC() < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        offerAdaptiveEconomyButtons(player, game);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
    }
}
