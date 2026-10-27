package ti4.service.leader.agent.modules.homebrew;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.service.RemoveCommandCounterService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class CheiranAgent extends TargetedAgent {

    static final String ID = "cheiranagent";
    private static final String REMOVE_STEP = "cheiranAgentStep2_";
    private static final String PLACE_STEP = "cheiranAgentStep3_";

    public CheiranAgent() {
        super(ID, "Operator Kkavras, the Cheiran");
    }

    public static Optional<Button> button(Game game, Player cheiran) {
        if (tilesToMoveATokenFrom(game, cheiran).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Buttons.green(
                ownedButtonId(cheiran, ID, cheiran.getFaction()),
                useLabel(cheiran, ID, "Cheiran Agent"),
                FactionEmojis.cheiran));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (tilesToMoveATokenFrom(game, target).isEmpty()) {
            return Optional.of(target.getFactionEmojiOrColor()
                    + " has no command token next to a system with their structures and none of their command tokens");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        List<Button> systems = tilesToMoveATokenFrom(use.game(), target).stream()
                .map(tile -> Buttons.green(
                        ownedStepId(target, REMOVE_STEP + tile.getPosition(), use.viaYssaril()),
                        tile.getRepresentationForButtons(use.game(), target)))
                .toList();
        return AgentOutcome.of(Message.addressedTo(
                target, ", please choose the system you wish to remove your command token from.", systems));
    }

    @ButtonHandler(REMOVE_STEP)
    static void removeToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        AgentStepIds.Parsed step = AgentStepIds.parse(buttonID);
        Tile origin = game.getTileByPosition(StringUtils.substringAfter(step.id(), REMOVE_STEP));
        if (origin == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that system.");
            return;
        }
        RemoveCommandCounterService.fromTile(player.getColor(), origin, game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmojiOrColor() + " removed 1 command token from "
                        + origin.getRepresentationForButtons(game, player) + " using "
                        + AgentLifecycle.stepAgentName(game, player, ID, step.viaYssaril()) + ".");
        List<Tile> destinations = destinations(game, player, origin);
        if (destinations.size() == 1) {
            placeToken(event, game, player, destinations.getFirst(), step);
            return;
        }
        List<Button> buttons = destinations.stream()
                .map(tile -> Buttons.green(
                        ownedStepId(player, PLACE_STEP + tile.getPosition(), step.viaYssaril()),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", please choose the adjacent system that you wish to place your command token in.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_STEP)
    static void placeTokenStep(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        AgentStepIds.Parsed step = AgentStepIds.parse(buttonID);
        Tile destination = game.getTileByPosition(StringUtils.substringAfter(step.id(), PLACE_STEP));
        if (destination == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that system.");
            return;
        }
        placeToken(event, game, player, destination, step);
    }

    static List<Tile> tilesToMoveATokenFrom(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> CommandCounterHelper.hasCC(player, tile))
                .filter(tile -> !destinations(game, player, tile).isEmpty())
                .toList();
    }

    static List<Tile> destinations(Game game, Player player, Tile origin) {
        List<Tile> tiles = new ArrayList<>();
        for (String position : FoWHelper.getAdjacentTiles(game, origin.getPosition(), player, false)) {
            Tile tile = game.getTileByPosition(position);
            if (tile != null
                    && ButtonHelperAgents.doesTileHaveAStructureInIt(player, tile)
                    && !CommandCounterHelper.hasCC(player, tile)) {
                tiles.add(tile);
            }
        }
        return tiles;
    }

    private static void placeToken(
            ButtonInteractionEvent event, Game game, Player player, Tile destination, AgentStepIds.Parsed step) {
        CommandCounterHelper.addCC(event, player, destination, true);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmojiOrColor() + " placed 1 command token in "
                        + destination.getRepresentationForButtons(game, player) + " using "
                        + AgentLifecycle.stepAgentName(game, player, ID, step.viaYssaril()) + ".");
        ButtonHelper.deleteMessage(event);
    }
}
