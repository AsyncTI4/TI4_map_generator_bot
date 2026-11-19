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
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class IronLeadersHandler {
    public static final String PRODUCTION_SYSTEM_PROMPT =
            ", choose a system to produce 1 unit with _Shipwright Kastel_.";
    private static final String SELECT_PRODUCTION_SYSTEM = "ironAgentProductionSystem_";
    private static final String SELECT_HERO_PRODUCTION_SYSTEM = "ironHeroProductionSystem_";
    private static final String AGENT_OWNER = "ironAgentOwner_";
    private static final String SELECT_COPY_PRODUCTION_SYSTEM = "ironAgentCopyProductionSystem_";

    public static void setShipwrightKastelOwner(Game game, Player owner, Player target) {
        String ownerKey = AGENT_OWNER + target.getFaction();
        if (owner == target) {
            game.removeStoredValue(ownerKey);
        } else {
            game.setStoredValue(ownerKey, owner.getFaction());
        }
    }

    private static void offerShipwrightKastelCopyProduction(Game game, Player target) {
        String ownerKey = AGENT_OWNER + target.getFaction();
        String ownerFaction = game.getStoredValue(ownerKey);
        game.removeStoredValue(ownerKey);
        Player owner = game.getPlayerFromColorOrFaction(ownerFaction);
        if (owner == null) return;
        List<Button> buttons = getShipwrightKastelCopySystemButtons(game, owner, target.getFaction());
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                owner.getCorrectChannel(),
                owner.getRepresentationNoPing() + ", " + target.getRepresentationNoPing()
                        + " is producing 1 unit with _Shipwright Kastel_. You may produce the same unit in a system containing your ships for half its cost, rounded up.",
                NewStuffHelper.paginateWithPinnedButtons(
                        buttons,
                        List.of(Buttons.DECLINE_DELETE_BUTTONS),
                        owner.factionButtonChecker() + SELECT_COPY_PRODUCTION_SYSTEM + target.getFaction() + "|",
                        24,
                        0));
    }

    @ButtonHandler(SELECT_COPY_PRODUCTION_SYSTEM)
    public static void selectShipwrightKastelCopyProductionSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_COPY_PRODUCTION_SYSTEM.length()).split("\\|", 2);
        if (payload.length == 2
                && NewStuffHelper.checkAndHandlePaginationChange(
                        event,
                        event.getMessageChannel(),
                        getShipwrightKastelCopySystemButtons(game, player, payload[0]),
                        player.getRepresentationNoPing()
                                + ", choose a system in which to produce with _Shipwright Kastel_...",
                        player.factionButtonChecker() + SELECT_COPY_PRODUCTION_SYSTEM + payload[0] + "|",
                        buttonID)) {
            return;
        }
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[1]) : null;
        if (tile == null || !FoWHelper.playerHasShipsInSystem(player, tile)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons =
                Helper.getPlaceUnitButtons(event, player, game, tile, "ironAgentCopy", "placeOneNDone_dontskip");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", produce the same unit for half its cost, rounded up, with _Shipwright Kastel_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getShipwrightKastelCopySystemButtons(Game game, Player player, String targetFaction) {
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker()
                                + SELECT_COPY_PRODUCTION_SYSTEM
                                + targetFaction
                                + "|"
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    public static List<Button> agentProductionSystemButtons(Game game, Player target) {
        return NewStuffHelper.paginateWithPinnedButtons(
                getProductionSystemButtons(game, target),
                List.of(),
                target.factionButtonChecker() + SELECT_PRODUCTION_SYSTEM,
                25,
                0);
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
        if (tile == null || !hasProductionUnit(player, game, tile)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        offerShipwrightKastelCopyProduction(game, player);
        List<Button> buttons =
                Helper.getPlaceUnitButtons(event, player, game, tile, "ironAgent", "placeOneNDone_dontskip");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", produce 1 unit in "
                        + tile.getRepresentationForButtons(game, player)
                        + " with _Shipwright Kastel_.",
                buttons);
        ButtonHelper.deleteMessage(event);
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
        return player.getRepresentation() + PRODUCTION_SYSTEM_PROMPT;
    }

    private static String heroProductionSystemMessage(Player player) {
        return player.getRepresentation()
                + ", choose a system in which to resolve PRODUCTION with _Hyper Forge - Mecha Prime_.";
    }
}
