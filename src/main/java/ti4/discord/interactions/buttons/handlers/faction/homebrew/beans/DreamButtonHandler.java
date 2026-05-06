package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
public class DreamButtonHandler {

    private static final String NEXUS_TOKEN = "beansnexus";
    private static final String LITURGY_I_UNIT = "dream_destroyer";
    private static final String LITURGY_II_UNIT = "dream_destroyer2";
    private static final String LITURGY_II_TECH = "bedreamdd";
    private static final String OFFER_ADD_NEXUS_BUTTON_ID = "beans_dream_offer_add_nexus";
    private static final String OFFER_MOVE_NEXUS_BUTTON_ID = "beans_dream_offer_move_nexus";
    private static final String BACK_TO_LITURGY_MENU_BUTTON_ID = "beans_dream_liturgy_menu_back";


    public static void offerLiturgyButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        Tile tile = getActiveLiturgyTile(game, player);
        if (tile == null) {
            return;
        }
        sendLiturgyMenu(game, player);
    }

    public static void showLiturgyMenu(ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = getActiveLiturgyTile(game, player);
        if (tile == null) return;

        ButtonHelper.deleteMessage(event);
        sendLiturgyMenu(game, player);
    }

    private static void sendLiturgyMenu(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(OFFER_ADD_NEXUS_BUTTON_ID, "Add Nexus Token"));
        buttons.add(Buttons.blue(OFFER_MOVE_NEXUS_BUTTON_ID, "Move Nexus Token"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation()
                        + " you may resolve _Liturgy_ unit ability now by placing or moving 1 nexus token.",
                buttons);
    }

    public static void offerAddNexusButtons(ButtonInteractionEvent event, Game game, Player player) {
        Tile activeTile = getActiveLiturgyTile(game, player);
        if (activeTile == null) return;

        ButtonHelper.deleteMessage(event);
        if (countNexusTokens(game) >= 3) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getRepresentation() + " cannot add a nexus token because all 3 are already on the map.");
            return;
        }

        List<Tile> tilesWithUnits = hasLiturgyII(player, activeTile)
                ? getTilesContainingPlayersUnits(game, player)
                : List.of(activeTile);
        List<Button> buttons = new ArrayList<>();
        for (Tile tileWithUnits : tilesWithUnits) {
            buttons.add(Buttons.green("beans_dream_add_nexus_" + tileWithUnits.getPosition(),
                    "Place nexus in " + tileWithUnits.getRepresentationForButtons(game, player)));
        }
        buttons.add(Buttons.gray(BACK_TO_LITURGY_MENU_BUTTON_ID, "Back"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation() + " choose where to add a nexus token:",
                buttons);
    }

    public static void offerMoveNexusButtons(ButtonInteractionEvent event, Game game, Player player) {
        Tile activeTile = getActiveLiturgyTile(game, player);
        if (activeTile == null) return;

        ButtonHelper.deleteMessage(event);
        List<Tile> tilesWithUnits = hasLiturgyII(player, activeTile)
                ? getTilesContainingPlayersUnits(game, player)
                : List.of(activeTile);
        List<Tile> tilesWithNexusTokens = getTilesContainingNexusTokens(game);
        List<Button> buttons = new ArrayList<>();
        for (Tile fromTile : tilesWithNexusTokens) {
            for (Tile toTile : tilesWithUnits) {
                if (fromTile.getPosition().equals(toTile.getPosition())) {
                    continue;
                }
                buttons.add(Buttons.blue("beans_dream_move_nexus_from_" + fromTile.getPosition() + "_to_" + toTile.getPosition(),
                        "Move from " + fromTile.getRepresentationForButtons(game, player)
                                + " to " + toTile.getRepresentationForButtons(game, player)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getRepresentation() + " has no valid nexus token moves available right now.");
            return;
        }
        buttons.add(Buttons.gray(BACK_TO_LITURGY_MENU_BUTTON_ID, "Back"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation() + " choose where to move a nexus token:",
                buttons);
    }

    static boolean hasLiturgyII(Player player, Tile tile) {
        return ButtonHelper.doesPlayerHaveUnitHere(LITURGY_II_UNIT, player, tile)
                || (player.hasTech(LITURGY_II_TECH)
                        && ButtonHelper.doesPlayerHaveUnitHere(LITURGY_I_UNIT, player, tile));
    }

    static List<Tile> getTilesContainingPlayersUnits(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.containsPlayersUnits(player))
                .toList();
    }

    static int countNexusTokens(Game game) {
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN);
        int count = 0;
        for (Tile tile : game.getTileMap().values()) {
            count += (int) tile.getSpaceUnitHolder().getTokenList().stream()
                    .filter(tokenId::equals)
                    .count();
        }
        return count;
    }

    static List<Tile> getTilesContainingNexusTokens(Game game) {
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN);
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder().getTokenList().stream().anyMatch(tokenId::equals))
                .toList();
    }

    private static Tile getActiveLiturgyTile(Game game, Player player) {
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            return null;
        }
        String activeSystem = game.getActiveSystem();
        if (activeSystem == null || activeSystem.isBlank()) {
            return null;
        }
        Tile tile = game.getTileByPosition(activeSystem);
        if (tile == null) {
            return null;
        }
        if (!ButtonHelper.doesPlayerHaveUnitHere(LITURGY_I_UNIT, player, tile)
                && !ButtonHelper.doesPlayerHaveUnitHere(LITURGY_II_UNIT, player, tile)) {
            return null;
        }
        return tile;
    }

    public static void addNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.replace("beans_dream_add_nexus_", "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN);
        tile.addToken(tokenId, "space");
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(event,
            player.getRepresentation() + " added a nexus token to " + tile.getRepresentationForButtons(game, player) + ".");
    }

    public static void moveNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String data = buttonID.replace("beans_dream_move_nexus_from_", "");
        String[] parts = data.split("_to_");
        if (parts.length != 2) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse nexus move request.");
            return;
        }
        Tile fromTile = game.getTileByPosition(parts[0]);
        Tile toTile = game.getTileByPosition(parts[1]);
        if (fromTile == null || toTile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find one of those systems.");
            return;
        }
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN);
        if (!fromTile.removeToken(tokenId, "space")) {
            MessageHelper.sendMessageToEventChannel(event, "The source system does not contain a nexus token.");
            return;
        }
        toTile.addToken(tokenId, "space");
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(event,
            player.getRepresentation() + " moved a nexus token to " + toTile.getRepresentationForButtons(game, player) + ".");
    }

    public static void removeNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.replace("beans_dream_remove_nexus_", "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }
        if ("statusHomework".equalsIgnoreCase(game.getPhaseOfGame())
                && !getTilesContainingNexusTokensWithPlayersShips(game, player).stream()
                        .map(Tile::getPosition)
                        .toList()
                        .contains(position)) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "You can only resolve _The Waking_ in a system that contains both your ships and a Dreaming Throne nexus token.");
            return;
        }
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN);
        boolean removed = tile.removeToken(tokenId, "space");
        if (!removed) {
            MessageHelper.sendMessageToEventChannel(event, "That system does not contain a nexus token.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(event,
            player.getRepresentation() + " removed a nexus token from " + tile.getRepresentationForButtons(game, player) + ".");
    }

    public static void offerTheWakingButtons(Game game) {
        for (Player player : game.getRealPlayers()) {
            List<Tile> eligibleTiles = getTilesContainingNexusTokensWithPlayersShips(game, player);
            if (eligibleTiles.isEmpty()) {
                continue;
            }
            List<Button> buttons = new ArrayList<>();
            for (Tile tile : eligibleTiles) {
                buttons.add(Buttons.red(
                        "beans_dream_remove_nexus_" + tile.getPosition(),
                        "Remove nexus from " + tile.getRepresentationForButtons(game, player)));
            }
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " may resolve _The Waking_ now: remove 1 nexus token from a system that contains both your ships and a nexus token.",
                    buttons);
        }
    }

    static List<Tile> getTilesContainingNexusTokensWithPlayersShips(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile))
                .filter(DreamButtonHandler::tileContainsNexusToken)
                .toList();
    }

    private static boolean tileContainsNexusToken(Tile tile) {
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN);
        return tile.getSpaceUnitHolder().getTokenList().stream().anyMatch(token ->
                (tokenId != null && tokenId.equals(token))
                        || NEXUS_TOKEN.equalsIgnoreCase(token)
                        || NEXUS_TOKEN.equalsIgnoreCase(Mapper.getTokenKey(token)));
    }
}
