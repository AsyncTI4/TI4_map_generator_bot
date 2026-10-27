package ti4.service.leader.agent.modules.homebrew;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.UntargetedAgent;

public final class FogAllianceAgent extends UntargetedAgent {

    static final String ID = "fogallianceagent";
    private static final String DESTINATION_STEP = "fogAllianceAgentStep2_";
    private static final String MOVE_STEP = "fogAllianceAgentStep3_";

    public FogAllianceAgent() {
        super(ID, "The Cab Driver");
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player payload) {
        if (originSystems(game, user).isEmpty()) {
            return Optional.of("You have no ships in a system without your command token");
        }
        if (destinations(game, user).isEmpty()) {
            return Optional.of("No alliance member has ships in a system you can move to");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player user = use.user();
        List<Button> systems = originSystems(use.game(), user).stream()
                .map(tile -> Buttons.green(
                        ownedStepId(user, DESTINATION_STEP + tile.getPosition(), false),
                        tile.getRepresentationForButtons(use.game(), user)))
                .toList();
        return AgentOutcome.of(
                Message.addressedTo(user, ", please choose the system that you wish to move ships from.", systems));
    }

    @ButtonHandler(DESTINATION_STEP)
    static void chooseDestination(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String origin = StringUtils.substringAfter(buttonID, DESTINATION_STEP);
        List<Button> buttons = destinations(game, player).stream()
                .map(tile -> Buttons.green(
                        ownedStepId(player, MOVE_STEP + tile.getPosition() + "_" + origin, false),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
        String message = buttons.isEmpty()
                ? player.getRepresentationNoPing() + ", no alliance member has ships in a system you can move to."
                : player.getRepresentationNoPing() + ", please choose the system you wish to move ships to.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    static List<Tile> originSystems(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile))
                .filter(tile -> !CommandCounterHelper.hasCC(player, tile))
                .toList();
    }

    static List<Tile> destinations(Game game, Player player) {
        Map<String, Tile> destinations = new LinkedHashMap<>();
        for (Tile tile : game.getTileMap().values()) {
            boolean allyHasShips = game.getRealPlayers().stream()
                    .filter(ally ->
                            ally != player && player.getAllianceMembers().contains(ally.getFaction()))
                    .anyMatch(ally -> FoWHelper.playerHasShipsInSystem(ally, tile));
            if (allyHasShips && !inOrNextToAHostileHomeSystem(game, player, tile)) {
                destinations.put(tile.getPosition(), tile);
            }
        }
        return List.copyOf(destinations.values());
    }

    private static boolean inOrNextToAHostileHomeSystem(Game game, Player player, Tile tile) {
        for (Player other : game.getRealPlayers()) {
            if (other == player || player.getAllianceMembers().contains(other.getFaction())) {
                continue;
            }
            Tile home = other.getHomeSystemTile();
            if (home != null
                    && FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true)
                            .contains(home.getPosition())) {
                return true;
            }
        }
        return false;
    }
}
