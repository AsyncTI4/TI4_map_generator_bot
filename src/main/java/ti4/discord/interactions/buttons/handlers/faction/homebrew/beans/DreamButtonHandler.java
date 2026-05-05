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
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
public class DreamButtonHandler {

    private static final String NEXUS_TOKEN = "beansnexus";


    public static void offerLiturgyButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            return;
        }
        String activeSystem = game.getActiveSystem();
        if (activeSystem == null || activeSystem.isBlank()) {
            return;
        }
        Tile tile = game.getTileByPosition(activeSystem);
        if (tile == null) {
            return;
        }

        if (!ButtonHelper.doesPlayerHaveUnitHere("dream_destroyer", player, tile)
                && !ButtonHelper.doesPlayerHaveUnitHere("dream_destroyer2", player, tile)) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        boolean hasLiturgyII = ButtonHelper.doesPlayerHaveUnitHere("dream_destroyer2", player, tile);
        List<Tile> tilesWithUnits = hasLiturgyII
                ? ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, Units.UnitType.values())
                : List.of(tile);

        if (countNexusTokens(game) < 3) {
            for (Tile tileWithUnits : tilesWithUnits) {
                buttons.add(Buttons.green("beans_dream_add_nexus_" + tileWithUnits.getPosition(),
                        "Place nexus in " + tileWithUnits.getRepresentationForButtons(game, player)));
            }
        }

        for (Tile tileWithUnits : tilesWithUnits) {
            buttons.add(Buttons.gray("beans_dream_move_nexus_from_" + tile.getPosition() + "_to_" + tileWithUnits.getPosition(),
                    "Move nexus to " + tileWithUnits.getRepresentationForButtons(game, player)));
        }

        if (buttons.isEmpty()) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation()
                        + " you may resolve _Liturgy_ (Dream Destroyer) now by placing or moving 1 nexus token.",
                buttons);
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

    public static void addNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.replace("beans_dream_add_nexus_", "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find that system.");
            return;
        }
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN);
        tile.addToken(tokenId, "space");
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
        String tokenId = Mapper.getTokenID(NEXUS_TOKEN);
        boolean removed = tile.removeToken(tokenId, "space");
        if (!removed) {
            MessageHelper.sendMessageToEventChannel(event, "That system does not contain a nexus token.");
            return;
        }
        MessageHelper.sendMessageToEventChannel(event,
            player.getRepresentation() + " removed a nexus token from " + tile.getRepresentationForButtons(game, player) + ".");
    }
}