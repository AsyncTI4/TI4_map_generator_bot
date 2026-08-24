package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum;

import java.util.ArrayList;
import java.util.Comparator;
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
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class CrystellumUnitHandler {
    private static final String USE_SHARD_SWARM = "useShardSwarm";
    private static final String SHARD_SWARM_SYSTEM = "shardSwarmSystem_";
    private static final String PLACE_SHARD_SWARM = "placeShardSwarm_";

    public static Button addShardSwarmStartTurnButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + USE_SHARD_SWARM, "Use SHARD SWARM", UnitEmojis.fighter);
    }

    @ButtonHandler(USE_SHARD_SWARM)
    public static void useShardSwarm(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getShardSwarmSystemButtons(game, player);

        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You do not control a planet in any eligible system.");
            return;
        }

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                getShardSwarmSystemMessage(player),
                getShardSwarmSystemButtonsPage(buttons, player));
    }

    @ButtonHandler(SHARD_SWARM_SYSTEM)
    public static void chooseShardSwarmSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> systemButtons = getShardSwarmSystemButtons(game, player);
        String pagePrefix = player.factionButtonChecker() + SHARD_SWARM_SYSTEM;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                player.getCorrectChannel(),
                systemButtons,
                getShardSwarmSystemMessage(player),
                pagePrefix,
                buttonID)) {
            return;
        }

        String position = buttonID.replace(SHARD_SWARM_SYSTEM, "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            return;
        }

        boolean controlsPlanetHere = tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> player.getPlanets().contains(planet.getName()));
        if (!controlsPlanetHere || !player.hasUnit("crystellum_fighter3")) {
            return;
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", place up to 2 fighters in "
                        + tile.getRepresentationForButtons(game, player)
                        + " with SHARD SWARM.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + PLACE_SHARD_SWARM + position + "_1",
                                "Place 1 Fighter",
                                UnitEmojis.fighter),
                        Buttons.green(
                                player.factionButtonChecker() + PLACE_SHARD_SWARM + position + "_2",
                                "Place 2 Fighters",
                                UnitEmojis.fighter),
                        Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline")));
    }

    @ButtonHandler(PLACE_SHARD_SWARM)
    public static void placeShardSwarms(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.replace(PLACE_SHARD_SWARM, "");
        int separator = payload.lastIndexOf('_');
        if (separator < 1) {
            return;
        }

        Tile tile = game.getTileByPosition(payload.substring(0, separator));
        if (tile == null || !player.hasUnit("crystellum_fighter3")) {
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(payload.substring(separator + 1));
        } catch (NumberFormatException e) {
            return;
        }

        amount = Math.min(2, Math.max(1, amount));
        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " fighter");

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " placed "
                        + amount
                        + " fighter"
                        + (amount == 1 ? "" : "s")
                        + " in "
                        + tile.getRepresentationForButtons(game, player)
                        + " with SHARD SWARM.");
    }

    private static List<Button> getShardSwarmSystemButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> player.getPlanets().contains(planet.getName())))
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + SHARD_SWARM_SYSTEM + tile.getPosition(),
                        "Choose " + tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static String getShardSwarmSystemMessage(Player player) {
        return player.getRepresentationUnfogged()
                + ", choose a system containing a planet you control for SHARD SWARM.";
    }

    private static List<Button> getShardSwarmSystemButtonsPage(List<Button> systemButtons, Player player) {
        if (systemButtons.size() < 25) {
            List<Button> buttons = new ArrayList<>(systemButtons);
            buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
            return buttons;
        }

        return NewStuffHelper.buttonPagination(
                systemButtons, null, player.factionButtonChecker() + SHARD_SWARM_SYSTEM, 25, 0, true);
    }
}
