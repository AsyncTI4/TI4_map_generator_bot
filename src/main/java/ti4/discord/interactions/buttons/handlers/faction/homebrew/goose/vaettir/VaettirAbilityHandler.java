package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.vaettir;

import java.util.List;
import java.util.Map;
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
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.MoveUnitService;

@UtilityClass
public class VaettirAbilityHandler {
    private static final List<String> ROOT_TYPES = List.of("cultural", "industrial", "hazardous");
    private static final String USE_TAKE_ROOT = "useTakeRoot";
    private static final String SELECT_ROOT_ATTACHMENT = "selectRootAttachment_";
    private static final String SELECT_ROOT_PLANET = "selectRootPlanet_";
    private static final String USE_COMMUNION = "useCommunion_";
    private static final String MOVE_COMMUNION_UNIT = "moveCommunionUnit_";
    private static final String SELECT_COMMUNION_DESTINATION = "selectCommunionDestination_";
    private static final String MOVE_COMMUNION_DESTINATION = "moveCommunionDestination_";
    private static final String PRODUCE_COMMUNION_UNITS = "produceCommunionUnits_";
    private static final String REMOVE_COMMUNION_ROOT = "removeCommunionRoot_";

    public static List<String> getRootTypes() {
        return ROOT_TYPES;
    }

    public static int getPlanetTraitCount(Planet planet) {
        return (int) ROOT_TYPES.stream()
                .filter(rootType -> planet.getPlanetTypes().contains(rootType)
                        || planet.getTokenList().contains("attachment_root_" + rootType + ".png"))
                .count();
    }

    public static void offerTakeRoot(Game game) {
        for (Player player : game.getRealPlayers()) {
            offerTakeRoot(game, player);
        }
    }

    private static void offerTakeRoot(Game game, Player player) {
        if (!player.hasAbility("take_root")) {
            return;
        }
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null || activeSystem.getPlanetUnitHolders().isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player == game.getActivePlayer() ? player.getCorrectChannel() : player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", you may use **Take Root** to add a Root token with a trait that planet does not already have"
                        + " to a planet in the active system."
                        + "\n-# This adds an attachment in async.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + USE_TAKE_ROOT, "Use Take Root", FactionEmojis.vaettir),
                        Buttons.DECLINE_DELETE_BUTTONS));
    }

    @ButtonHandler(USE_TAKE_ROOT)
    public static void selectRootPlanet(ButtonInteractionEvent event, Game game, Player player) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = activeSystem.getPlanetUnitHolders().stream()
                .filter(planet -> ROOT_TYPES.stream().anyMatch(rootType -> canPlaceRootToken(game, planet, rootType)))
                .map(Planet::getName)
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + SELECT_ROOT_PLANET + planet,
                        Helper.getPlanetRepresentation(planet, game),
                        FactionEmojis.vaettir))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose the planet to receive a Root token.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_ROOT_PLANET)
    public static void selectRootAttachment(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(SELECT_ROOT_PLANET.length());
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        Planet planet = activeSystem == null ? null : activeSystem.getUnitHolderFromPlanet(planetName);
        if (planet == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = ROOT_TYPES.stream()
                .filter(rootType -> canPlaceRootToken(game, planet, rootType))
                .map(rootType -> rootAttachmentButton(player, planet, rootType))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose the Root token to add to "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_ROOT_ATTACHMENT)
    public static void addRootAttachment(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_ROOT_ATTACHMENT.length()).split("\\|", 2);
        if (payload.length != 2 || !isRootType(payload[1])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        Planet planet = activeSystem == null ? null : activeSystem.getUnitHolderFromPlanet(payload[0]);
        if (planet == null || !canPlaceRootToken(game, planet, payload[1])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String attachment = "attachment_root_" + payload[1] + ".png";
        if (planet.addToken(attachment)) {
            syncOldGrowthRootCards(game);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " added the " + payload[1] + " Root token to "
                            + Helper.getPlanetRepresentation(planet.getName(), game)
                            + ".\n-# This adds an attachment in async.");
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void syncOldGrowthRootCards(Game game) {
        for (Player player : game.getRealPlayers()) {
            for (String rootType : ROOT_TYPES) {
                String rootCard = rootType + "_root_card";
                boolean controlsRoot = player.getPlanets().stream()
                        .map(game::getUnitHolderFromPlanet)
                        .anyMatch(planet -> planet != null
                                && planet.getTokenList().contains("attachment_root_" + rootType + ".png"));
                if (controlsRoot) {
                    player.addAbility(rootCard);
                } else {
                    player.removeAbility(rootCard);
                    player.removeExhaustedAbility(rootCard);
                }
            }
        }
    }

    public static boolean isRootTokenAvailable(Game game, String rootType) {
        return isRootType(rootType) && getRootTokenCount(game, rootType) < 4;
    }

    public static int getRootTokenCount(Game game, String rootType) {
        if (!isRootType(rootType)) {
            return 0;
        }
        return (int) game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(planet -> planet.getTokenList().contains("attachment_root_" + rootType + ".png"))
                .count();
    }

    public static void offerCommunion(Game game, Player player, Tile productionTile) {
        if (!player.hasAbility("communion")
                || productionTile == null
                || !containsRootToken(productionTile)
                || !hasProducedUnitsInTile(player, productionTile)) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may use **Communion** after producing units in a system that contains a Root token.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + USE_COMMUNION + productionTile.getPosition(),
                                "Use Communion",
                                FactionEmojis.vaettir),
                        Buttons.DECLINE_DELETE_BUTTONS));
    }

    @ButtonHandler(USE_COMMUNION)
    public static void useCommunion(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile productionTile = game.getTileByPosition(buttonID.substring(USE_COMMUNION.length()));
        if (productionTile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose how to resolve **Communion**.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + MOVE_COMMUNION_UNIT + productionTile.getPosition(),
                                "Move 1 Produced Unit",
                                FactionEmojis.vaettir),
                        Buttons.green(
                                player.factionButtonChecker() + PRODUCE_COMMUNION_UNITS + productionTile.getPosition(),
                                "Produce Units and Remove a Root Token",
                                FactionEmojis.vaettir)));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(MOVE_COMMUNION_UNIT)
    public static void selectCommunionUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String sourcePosition = buttonID.substring(MOVE_COMMUNION_UNIT.length());
        List<Button> buttons = player.getCurrentProducedUnits().entrySet().stream()
                .map(entry -> producedUnitButton(player, entry, sourcePosition))
                .filter(button -> button != null)
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose 1 produced unit to move.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_COMMUNION_DESTINATION)
    public static void selectCommunionDestination(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_COMMUNION_DESTINATION.length()).split("\\|", 3);
        if (payload.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = game.getTileMap().values().stream()
                .filter(VaettirAbilityHandler::containsRootToken)
                .filter(tile -> !tile.getPosition().equals(payload[0]))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + MOVE_COMMUNION_DESTINATION + payload[0] + "|" + payload[1] + "|"
                                + payload[2] + "|" + tile.getPosition(),
                        "Move to " + tile.getRepresentationForButtons(),
                        FactionEmojis.vaettir))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose a system containing a Root token.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(MOVE_COMMUNION_DESTINATION)
    public static void moveCommunionUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(MOVE_COMMUNION_DESTINATION.length()).split("\\|", 4);
        if (payload.length != 4) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile source = game.getTileByPosition(payload[0]);
        Tile destination = game.getTileByPosition(payload[3]);
        if (source == null || destination == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String unitList = "1 " + payload[1] + ("space".equals(payload[2]) ? "" : " " + payload[2]);
        MoveUnitService.moveUnits(event, source, game, player.getColor(), unitList, destination, "space");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " moved 1 produced unit to "
                        + destination.getRepresentationForButtons() + " with **Communion**.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PRODUCE_COMMUNION_UNITS)
    public static void selectCommunionRoot(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream()
                        .flatMap(planet -> ROOT_TYPES.stream()
                                .filter(rootType ->
                                        planet.getTokenList().contains("attachment_root_" + rootType + ".png"))
                                .map(rootType -> Buttons.green(
                                        player.factionButtonChecker() + REMOVE_COMMUNION_ROOT + tile.getPosition() + "|"
                                                + planet.getName() + "|" + rootType,
                                        "Remove " + rootType + " Root from "
                                                + Helper.getPlanetRepresentation(planet.getName(), game),
                                        FactionEmojis.vaettir))))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose a Root token to remove and produce units in that system.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(REMOVE_COMMUNION_ROOT)
    public static void produceCommunionUnits(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(REMOVE_COMMUNION_ROOT.length()).split("\\|", 3);
        if (payload.length != 3 || !isRootType(payload[2])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = game.getTileByPosition(payload[0]);
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(payload[1]);
        if (planet == null || !planet.removeToken("attachment_root_" + payload[2] + ".png")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        syncOldGrowthRootCards(game);
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "communion", "place");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " removed the " + payload[2] + " Root token from "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + " with **Communion**. Produce up to "
                        + planet.getPlanetTypes().size() + " units in " + tile.getRepresentationForButtons()
                        + ". This limit is player-enforced.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    private static Button rootAttachmentButton(Player player, Planet planet, String rootType) {
        return Buttons.green(
                player.factionButtonChecker() + SELECT_ROOT_ATTACHMENT + planet.getName() + "|" + rootType,
                "Add " + rootType + " Root Attachment",
                FactionEmojis.vaettir);
    }

    private static boolean canPlaceRootToken(Game game, Planet planet, String rootType) {
        return isRootTokenAvailable(game, rootType)
                && !planet.getPlanetTypes().contains(rootType)
                && !planet.getTokenList().contains("attachment_root_" + rootType + ".png");
    }

    public static boolean isRootType(String rootType) {
        return ROOT_TYPES.contains(rootType);
    }

    private static boolean containsRootToken(Tile tile) {
        return tile.getPlanetUnitHolders().stream().anyMatch(VaettirAbilityHandler::hasRootToken);
    }

    public static boolean hasRootToken(Planet planet) {
        return ROOT_TYPES.stream()
                .anyMatch(rootType -> planet.getTokenList().contains("attachment_root_" + rootType + ".png"));
    }

    private static boolean hasProducedUnitsInTile(Player player, Tile tile) {
        return player.getCurrentProducedUnits().keySet().stream()
                .map(key -> key.split("_", 3))
                .anyMatch(parts -> parts.length == 3 && tile.getPosition().equals(parts[1]));
    }

    private static Button producedUnitButton(Player player, Map.Entry<String, Integer> entry, String sourcePosition) {
        String[] parts = entry.getKey().split("_", 3);
        if (parts.length != 3 || entry.getValue() < 1 || !sourcePosition.equals(parts[1])) {
            return null;
        }
        return Buttons.green(
                player.factionButtonChecker() + SELECT_COMMUNION_DESTINATION + sourcePosition + "|" + parts[0] + "|"
                        + parts[2],
                "Move 1 " + parts[0]);
    }
}
