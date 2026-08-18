package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.dream;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class DreamUnitsHandler {
    private static final String NEXUS_TOKEN_ALIAS = "beansnexus";
    private static final String LITURGY_I_UNIT = "dream_destroyer";
    private static final String LITURGY_II_UNIT = "dream_destroyer2";
    private static final String LITURGY_II_TECH = "bedreamdd";
    private static final String LITURGY_MENU_BUTTON_ID = "dream_liturgy_menu_back";

    // Liturgy I / II

    public static void offerLiturgyButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        if (getActiveLiturgyTile(game, player) != null) {
            sendLiturgyMenu(game, player);
        }
    }

    @ButtonHandler("dream_liturgy_menu")
    public static void showLiturgyMenu(ButtonInteractionEvent event, Game game, Player player) {
        if (getActiveLiturgyTile(game, player) == null) return;

        ButtonHelper.deleteMessage(event);
        sendLiturgyMenu(game, player);
    }

    private static void sendLiturgyMenu(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("dream_offer_add_nexus", "Add Nexus Token"));
        buttons.add(Buttons.blue("dream_offer_move_nexus", "Move Nexus Token"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", you may resolve LITURGY now by placing or moving 1 nexus token.",
                buttons);
    }

    @ButtonHandler("dream_offer_add_nexus")
    public static void offerAddNexusButtons(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile activeTile = getActiveLiturgyTile(game, player);
        if (activeTile == null) return;

        if (getNexusTokenCount(game) >= 3) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", cannot add a nexus token because all 3 are already on the map.");
            return;
        }

        String message = player.getRepresentation() + ", choose where to add a nexus token:";
        List<Button> placementButtons = new ArrayList<>();
        for (Tile tileWithUnits : getLiturgyDestinations(game, player, activeTile)) {
            placementButtons.add(Buttons.green(
                    "dream_add_nexus" + tileWithUnits.getPosition(),
                    "Place Nexus in " + tileWithUnits.getRepresentationForButtons(game, player)));
        }
        List<Button> menuButtons =
                List.of(Buttons.gray(LITURGY_MENU_BUTTON_ID, "Back"), Buttons.red("deleteButtons", "Decline"));
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                player.getCorrectChannel(),
                placementButtons,
                menuButtons,
                message,
                "dream_offer_add_nexus_",
                buttonID)) {
            return;
        }
        ButtonHelper.deleteMessage(event);
        if (placementButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", has no valid nexus token placements available.");
            return;
        }
        List<Button> buttons = getLiturgyPagedButtons(placementButtons, menuButtons, "dream_offer_add_nexus_", 0);

        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("dream_offer_move_nexus")
    public static void offerMoveNexusButtons(ButtonInteractionEvent event, Game game, Player player) {
        Tile activeTile = getActiveLiturgyTile(game, player);
        if (activeTile == null) return;

        ButtonHelper.deleteMessage(event);
        List<Button> buttons = new ArrayList<>();
        for (Tile fromTile : getNexusTokenTiles(game)) {
            if (getLiturgyDestinations(game, player, activeTile).stream()
                    .anyMatch(toTile -> !toTile.getPosition().equals(fromTile.getPosition()))) {
                buttons.add(Buttons.blue(
                        "dream_offer_move_nexus_from_" + fromTile.getPosition(),
                        "Move Nexus From " + fromTile.getRepresentationForButtons(game, player)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", has no valid nexus token moves available right now.");
            return;
        }
        buttons.add(Buttons.gray(LITURGY_MENU_BUTTON_ID, "Back"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentation() + ", choose a nexus token to move:", buttons);
    }

    @ButtonHandler("dream_offer_move_nexus_from_")
    public static void offerMoveNexusDestinationButtons(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile activeTile = getActiveLiturgyTile(game, player);
        Tile fromTile = game.getTileByPosition(buttonID.replace("dream_offer_move_nexus_from_", ""));
        if (activeTile == null || fromTile == null || !DreamAbilitiesHandler.hasNexusToken(fromTile)) return;

        String message = player.getRepresentation() + ", choose where to move that nexus token:";
        List<Button> destinationButtons = getLiturgyDestinations(game, player, activeTile).stream()
                .filter(toTile -> !toTile.getPosition().equals(fromTile.getPosition()))
                .map(toTile -> Buttons.blue(
                        "dream_move_nexus" + fromTile.getPosition() + "_to_" + toTile.getPosition(),
                        "Move Nexus to " + toTile.getRepresentationForButtons(game, player)))
                .toList();
        List<Button> menuButtons =
                List.of(Buttons.gray(LITURGY_MENU_BUTTON_ID, "Back"), Buttons.red("deleteButtons", "Decline"));
        String pagePrefix = "dream_liturgy_move_destination_" + fromTile.getPosition() + "_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, player.getCorrectChannel(), destinationButtons, menuButtons, message, pagePrefix, buttonID)) {
            return;
        }
        ButtonHelper.deleteMessage(event);
        if (destinationButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", has no valid nexus token moves available right now.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                message,
                getLiturgyPagedButtons(destinationButtons, menuButtons, pagePrefix, 0));
    }

    @ButtonHandler("dream_liturgy_move_destination_")
    public static void changeMoveNexusDestinationPage(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String sourceAndPage = buttonID.replace("dream_liturgy_move_destination_", "");
        int pageSeparator = sourceAndPage.lastIndexOf("_page");
        if (pageSeparator < 1) return;
        Tile activeTile = getActiveLiturgyTile(game, player);
        Tile fromTile = game.getTileByPosition(sourceAndPage.substring(0, pageSeparator));
        if (activeTile == null || fromTile == null || !DreamAbilitiesHandler.hasNexusToken(fromTile)) return;

        String message = player.getRepresentation() + ", choose where to move that nexus token:";
        List<Button> destinationButtons = getLiturgyDestinations(game, player, activeTile).stream()
                .filter(toTile -> !toTile.getPosition().equals(fromTile.getPosition()))
                .map(toTile -> Buttons.blue(
                        "dream_move_nexus" + fromTile.getPosition() + "_to_" + toTile.getPosition(),
                        "Move Nexus to " + toTile.getRepresentationForButtons(game, player)))
                .toList();
        List<Button> menuButtons =
                List.of(Buttons.gray(LITURGY_MENU_BUTTON_ID, "Back"), Buttons.red("deleteButtons", "Decline"));
        NewStuffHelper.checkAndHandlePaginationChange(
                event,
                player.getCorrectChannel(),
                destinationButtons,
                menuButtons,
                message,
                "dream_liturgy_move_destination_" + fromTile.getPosition() + "_",
                buttonID);
    }

    @ButtonHandler("dream_add_nexus")
    public static void addNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.replace("dream_add_nexus", ""));
        Tile activeTile = getActiveLiturgyTile(game, player);
        if (tile == null
                || activeTile == null
                || getNexusTokenCount(game) >= 3
                || DreamAbilitiesHandler.hasNexusToken(tile)
                || !getLiturgyDestinations(game, player, activeTile).contains(tile)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid nexus token placement.");
            return;
        }
        if (!addNexusTokenToTile(game, tile)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid nexus token placement.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + ", added a nexus token to "
                        + tile.getRepresentationForButtons(game, player) + ".");
    }

    @ButtonHandler("dream_move_nexus")
    public static void moveNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("dream_move_nexus", "").split("_to_");
        if (parts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse nexus move request.");
            return;
        }
        Tile fromTile = game.getTileByPosition(parts[0]);
        Tile toTile = game.getTileByPosition(parts[1]);
        Tile activeTile = getActiveLiturgyTile(game, player);
        if (fromTile == null
                || toTile == null
                || activeTile == null
                || !DreamAbilitiesHandler.hasNexusToken(fromTile)
                || DreamAbilitiesHandler.hasNexusToken(toTile)
                || fromTile.getPosition().equals(toTile.getPosition())
                || !getLiturgyDestinations(game, player, activeTile).contains(toTile)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid nexus token move.");
            return;
        }
        if (!moveNexusTokenBetweenTiles(player, fromTile, toTile)) {
            MessageHelper.sendMessageToEventChannel(event, "The source system does not contain a nexus token.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + ", moved a nexus token to "
                        + toTile.getRepresentationForButtons(game, player) + ".");
    }

    private static Tile getActiveLiturgyTile(Game game, Player player) {
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) return null;

        String activeSystem = game.getActiveSystem();
        if (activeSystem == null || activeSystem.isBlank()) return null;

        Tile tile = game.getTileByPosition(activeSystem);
        if (tile == null
                || (!ButtonHelper.doesPlayerHaveUnitHere(LITURGY_I_UNIT, player, tile)
                        && !ButtonHelper.doesPlayerHaveUnitHere(LITURGY_II_UNIT, player, tile))) {
            return null;
        }
        return tile;
    }

    private static boolean hasLiturgyII(Player player, Tile tile) {
        return ButtonHelper.doesPlayerHaveUnitHere(LITURGY_II_UNIT, player, tile)
                || (player.hasTech(LITURGY_II_TECH)
                        && ButtonHelper.doesPlayerHaveUnitHere(LITURGY_I_UNIT, player, tile));
    }

    private static List<Tile> getLiturgyDestinations(Game game, Player player, Tile activeTile) {
        if (!hasLiturgyII(player, activeTile)) return List.of(activeTile);

        return game.getTileMap().values().stream()
                .filter(tile -> !tile.isHomeSystem(game))
                .filter(tile -> FoWHelper.playerHasActualShipsInSystem(player, tile))
                .filter(tile -> !FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game))
                .filter(tile -> !DreamAbilitiesHandler.hasNexusToken(tile))
                .toList();
    }

    private static List<Button> getLiturgyPagedButtons(
            List<Button> buttons, List<Button> menuButtons, String pagePrefix, int page) {
        if (buttons.size() <= 25 - menuButtons.size()) {
            List<Button> allButtons = new ArrayList<>(buttons);
            allButtons.addAll(menuButtons);
            return allButtons;
        }
        return NewStuffHelper.buttonPagination(buttons, menuButtons, pagePrefix, 25, page, false);
    }

    public static List<Tile> getNexusTokenTiles(Game game) {
        return game.getTileMap().values().stream()
                .filter(DreamAbilitiesHandler::hasNexusToken)
                .toList();
    }

    public static int getNexusTokenCount(Game game) {
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN_ALIAS);
        return (int) game.getTileMap().values().stream()
                .flatMap(tile -> tile.getSpaceUnitHolder().getTokenList().stream())
                .filter(token -> isNexusToken(token, tokenId))
                .count();
    }

    public static boolean addNexusTokenToTile(Game game, Tile tile) {
        if (game == null
                || tile == null
                || DreamAbilitiesHandler.hasNexusToken(tile)
                || getNexusTokenCount(game) >= 3) {
            return false;
        }
        tile.addToken(Mapper.getTokenID(NEXUS_TOKEN_ALIAS), "space");
        return true;
    }

    public static boolean moveNexusTokenBetweenTiles(Player player, Tile fromTile, Tile toTile) {
        String nexusToken = getPhysicalNexusToken(fromTile);
        if (nexusToken == null || !fromTile.removeToken(nexusToken, "space")) return false;

        if (!addNexusTokenToTile(player.getGame(), toTile)) {
            fromTile.addToken(nexusToken, "space");
            return false;
        }
        CommanderUnlockCheckService.checkPlayer(player, "dream");
        return true;
    }

    private static String getPhysicalNexusToken(Tile tile) {
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN_ALIAS);
        return tile.getSpaceUnitHolder().getTokenList().stream()
                .filter(token -> isNexusToken(token, tokenId))
                .findFirst()
                .orElse(null);
    }

    private static boolean isNexusToken(String token, String tokenId) {
        return (tokenId != null && tokenId.equalsIgnoreCase(token))
                || NEXUS_TOKEN_ALIAS.equalsIgnoreCase(token)
                || NEXUS_TOKEN_ALIAS.equalsIgnoreCase(Mapper.getTokenKey(token));
    }

    // The Recurring, the Dreaming Throne mech

    public static void offerRecurringMechButtons(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            int amount,
            String sourcePlanet,
            UnitKey unitKey) {
        if (player == null || unitKey == null) return;
        var unitModel = player.getUnitFromUnitKey(unitKey);
        if (unitModel == null || !"dream_mech".equalsIgnoreCase(unitModel.getId())) return;
        sendRecurringMechSpendButtons(event, game, player, amount, sourcePlanet);
    }

    private static void sendRecurringMechSpendButtons(
            GenericInteractionCreateEvent event, Game game, Player player, int amount, String sourcePlanet) {
        if (amount < 1) return;
        String source = sourcePlanet == null || sourcePlanet.isBlank() || "space".equalsIgnoreCase(sourcePlanet)
                ? "none"
                : sourcePlanet;
        List<Button> placeButtons = getRecurringMechPlanetButtons(game, player, amount, source);
        if (placeButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", has no valid planet they control for _The Recurring_ in a system with a nexus token or flagship.");
            return;
        }

        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        buttons.add(Buttons.green("dream_recurring_mech_paid_" + amount + "_from_" + source, "Done Spending"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", may spend 1 influence to place "
                        + (amount == 1 ? "_The Recurring_" : "1 of " + amount + " destroyed _Recurring_ mechs")
                        + " on another planet you control in a system that contains a nexus token or your flagship. After spending, click Done Spending to choose the destination.",
                buttons);
    }

    @ButtonHandler("dream_recurring_mech_paid_")
    public static void offerRecurringMechPlacementButtons(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasUnit("dream_mech")) return;
        String[] parts = buttonID.replace("dream_recurring_mech_paid_", "").split("_from_", 2);
        if (parts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse that _Recurring_ payment confirmation.");
            return;
        }

        int remaining = Integer.parseInt(parts[0]);
        List<Button> buttons = getRecurringMechPlanetButtons(game, player, remaining, parts[1]);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "There are no valid planets for _The Recurring_ anymore.");
            return;
        }

        buttons.add(Buttons.red("deleteButtons", "Decline"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose where to place _The Recurring_.",
                buttons);
    }

    @ButtonHandler("dream_recurring_mech_")
    public static void placeRecurringMech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasUnit("dream_mech")) return;
        String[] parts = buttonID.replace("dream_recurring_mech_", "").split("_to_", 2);
        if (parts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse that _Recurring_ placement.");
            return;
        }
        int remaining = Integer.parseInt(parts[0]);
        String[] planetParts = parts[1].split("_from_", 2);
        if (planetParts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse that _Recurring_ destination.");
            return;
        }

        String planet = planetParts[0];
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null
                || getRecurringMechPlanetButtons(game, player, remaining, planetParts[1]).stream()
                        .noneMatch(button -> button.getCustomId().equals(buttonID))) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid planet for _The Recurring_.");
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planet);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + ", placed _The Recurring_ on "
                        + Helper.getPlanetRepresentation(planet, game) + ".");
        if (remaining > 1) sendRecurringMechSpendButtons(event, game, player, remaining - 1, planetParts[1]);
    }

    private static List<Button> getRecurringMechPlanetButtons(
            Game game, Player player, int remaining, String sourcePlanet) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!DreamAbilitiesHandler.hasNexusTokenOrDreamFlagship(game, tile)) continue;
            for (var planet : tile.getPlanetUnitHolders()) {
                if (planet.getName().equalsIgnoreCase(sourcePlanet)
                        || !player.getPlanets().contains(planet.getName())) {
                    continue;
                }
                buttons.add(Buttons.green(
                        "dream_recurring_mech_" + remaining + "_to_" + planet.getName() + "_from_" + sourcePlanet,
                        "Place on " + Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        return buttons;
    }
}
