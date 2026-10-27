package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.iron;

import java.util.ArrayList;
import java.util.List;
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
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class IronLeadersHandler {
    private static final String SELECT_PRODUCTION_SYSTEM = "ironAgentProductionSystem_";
    private static final String SELECT_HERO_PRODUCTION_SYSTEM = "ironHeroProductionSystem_";
    private static final String PRODUCTION_CONTEXT = "ironAgentProduction_";

    public static void offerShipwrightKastelProduction(Game game, Player target) {
        List<Button> buttons = getProductionSystemButtons(game, target);

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentationNoPing() + " has no system containing one of their units with PRODUCTION.");
        } else {
            NewStuffHelper.sendPaginated(
                    target.getCorrectChannel(),
                    productionSystemMessage(target),
                    buttons,
                    List.of(),
                    target.factionButtonChecker() + SELECT_PRODUCTION_SYSTEM);
        }
    }

    @ButtonHandler(SELECT_PRODUCTION_SYSTEM)
    public static void selectShipwrightKastelProductionSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String message = productionSystemMessage(player);
        List<Button> systemButtons = getProductionSystemButtons(game, player);
        String prefix = player.factionButtonChecker() + SELECT_PRODUCTION_SYSTEM;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), systemButtons, message, prefix, buttonID)) {
            return;
        }

        String position = buttonID.substring(SELECT_PRODUCTION_SYSTEM.length());
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        game.setStoredValue(PRODUCTION_CONTEXT + player.getFaction(), position);
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "ironAgent", "place");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", produce 1 unit in "
                        + tile.getRepresentationForButtons(game, player)
                        + ". Its cost is reduced by 1 by _Shipwright Kastel_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static int applyShipwrightKastelProductionDiscount(Game game, Player player, int cost) {
        String key = PRODUCTION_CONTEXT + player.getFaction();
        if (game.getStoredValue(key).isBlank()) {
            return cost;
        }
        game.removeStoredValue(key);
        return Math.max(0, cost - 1);
    }

    public static void startAdmiralThalenHero(Game game, Player player) {
        List<String> readiedPlanets = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (!player.getPlanets().contains(planet.getName())
                        || !player.getExhaustedPlanets().contains(planet.getName())
                        || Helper.getProductionValueOfUnitHolder(player, game, tile, planet) < 1) {
                    continue;
                }
                player.refreshPlanet(planet.getName());
                readiedPlanets.add(Helper.getPlanetRepresentation(planet.getName(), game));
            }
        }

        String readyMessage = readiedPlanets.isEmpty()
                ? player.getRepresentationNoPing() + " readied no exhausted planets with _Hyper Forge - Mecha Prime_."
                : player.getRepresentationNoPing()
                        + " readied "
                        + String.join(", ", readiedPlanets)
                        + " with _Hyper Forge - Mecha Prime_.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), readyMessage);
        sendAdmiralThalenProductionSystemButtons(game, player);
    }

    @ButtonHandler(SELECT_HERO_PRODUCTION_SYSTEM)
    public static void selectAdmiralThalenProductionSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String message = heroProductionSystemMessage(player);
        List<Button> systemButtons = getProductionSystemButtons(game, player, SELECT_HERO_PRODUCTION_SYSTEM);
        String prefix = player.factionButtonChecker() + SELECT_HERO_PRODUCTION_SYSTEM;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), systemButtons, message, prefix, buttonID)) {
            return;
        }

        Tile tile = game.getTileByPosition(buttonID.substring(SELECT_HERO_PRODUCTION_SYSTEM.length()));
        if (tile == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "ironHero", "place");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", resolve PRODUCTION in "
                        + tile.getRepresentationForButtons(game, player)
                        + " with _Hyper Forge - Mecha Prime_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasProductionUnit(Player player, Game game, Tile tile) {
        return tile.getUnitHolders().values().stream()
                .anyMatch(holder -> Helper.getProductionValueOfUnitHolder(player, game, tile, holder) > 0);
    }

    private static List<Button> getProductionSystemButtons(Game game, Player player) {
        return getProductionSystemButtons(game, player, SELECT_PRODUCTION_SYSTEM);
    }

    private static List<Button> getProductionSystemButtons(Game game, Player player, String selectionPrefix) {
        return game.getTileMap().values().stream()
                .filter(tile -> hasProductionUnit(player, game, tile))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + selectionPrefix + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static void sendAdmiralThalenProductionSystemButtons(Game game, Player player) {
        List<Button> buttons = getProductionSystemButtons(game, player, SELECT_HERO_PRODUCTION_SYSTEM);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has no system containing one of their units with PRODUCTION.");
            return;
        }
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(),
                heroProductionSystemMessage(player),
                buttons,
                List.of(),
                player.factionButtonChecker() + SELECT_HERO_PRODUCTION_SYSTEM);
    }

    private static String productionSystemMessage(Player player) {
        return player.getRepresentation()
                + ", choose a system to produce 1 unit with _Shipwright Kastel_. Its cost is reduced by 1.";
    }

    private static String heroProductionSystemMessage(Player player) {
        return player.getRepresentation()
                + ", choose a system in which to resolve PRODUCTION with _Hyper Forge - Mecha Prime_.";
    }
}
