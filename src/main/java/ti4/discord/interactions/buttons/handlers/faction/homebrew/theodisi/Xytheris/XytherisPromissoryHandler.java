package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Xytheris;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.transaction.SendPromissoryService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class XytherisPromissoryHandler {
    private static final String SWARM_SPAWN = "thpnxytheris";
    private static final String USE_SWARM_SPAWN = "useSwarmSpawnPn_";
    private static final String SELECT_SWARM_SYSTEM = "selectSwarmSpawnSystem_";

    public static void offerXytherisPnButton(Game game, Player player, int hits) {
        if (game == null
                || player == null
                || !player.hasPlayablePromissoryInHand(SWARM_SPAWN)
                || player.getPromissoryNotesInPlayArea().contains(SWARM_SPAWN)
                || getEligibleSwarmSpawnTiles(game, player).isEmpty()) {
            return;
        }

        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + USE_SWARM_SPAWN + hits,
                        "Use Swarm Spawn",
                        FactionEmojis.xytheris),
                Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", you produced " + hits + " hit" + (hits == 1 ? "" : "s")
                        + " with a unit ability. You may play _Swarm Spawn_ to place " + hits
                        + " fighter" + (hits == 1 ? "" : "s") + " in a system containing one of your ships:",
                buttons);
    }

    @ButtonHandler(USE_SWARM_SPAWN)
    public static void selectSwarmSpawnSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null
                || player == null
                || !player.hasPlayablePromissoryInHand(SWARM_SPAWN)
                || player.getPromissoryNotesInPlayArea().contains(SWARM_SPAWN)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String hits = buttonID.substring(USE_SWARM_SPAWN.length());
        int h = hits.isEmpty() ? 0 : Integer.parseInt(hits);

        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getEligibleSwarmSpawnTiles(game, player)) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_SWARM_SYSTEM + h + "|" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.addPromissoryNoteToPlayArea(SWARM_SPAWN);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + " played _Swarm Spawn_. Please select the system to place " + h + " fighter"
                        + (h == 1 ? "" : "s") + " in:",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_SWARM_SYSTEM)
    public static void resolveSwarmSpawn(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null
                || player == null
                || !player.getPromissoryNotesInPlayArea().contains(SWARM_SPAWN)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring(SELECT_SWARM_SYSTEM.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String hits = parts[0];
        String tilePos = parts[1];

        int h = hits.isEmpty() ? 0 : Integer.parseInt(hits);
        Tile tile = game.getTileByPosition(tilePos);

        if (tile == null || !getEligibleSwarmSpawnTiles(game, player).contains(tile)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not locate a legal system for _Swarm Spawn_.");
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        Player owner = game.getPNOwner(SWARM_SPAWN);
        if (owner == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not locate the owner of _Swarm Spawn_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), h + " fighter");
        SendPromissoryService.returnPromissoryFromPlayAreaToOwner(event, game, player, owner, SWARM_SPAWN);
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " placed " + h + " fighter" + (h == 1 ? "" : "s") + " in "
                        + tile.getRepresentation() + " and returned _Swarm Spawn_ to its owner.");
    }

    private static List<Tile> getEligibleSwarmSpawnTiles(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasActualShipsInSystem(player, tile))
                .toList();
    }
}
