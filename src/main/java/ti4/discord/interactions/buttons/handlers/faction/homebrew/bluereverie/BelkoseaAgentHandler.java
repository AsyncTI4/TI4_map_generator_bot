package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

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
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.tactical.movement.BelkoseaAgentService;

@UtilityClass
public class BelkoseaAgentHandler {
    private static final String AGENT = "belkoseaagent";
    private static final String USE_AGENT = "useBelkoseaAgent";
    private static final String SELECT_SYSTEM = "selectBelkoseaAgentSystem_";

    @ButtonHandler(USE_AGENT)
    public static void useBelkoseaAgent(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(AGENT)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Razalka Gris must be ready to use this ability.");
            return;
        }

        List<Button> buttons = getSystemButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose the system whose other players' ships will not affect the active player's movement.";
        String prefix = player.factionButtonChecker() + USE_AGENT + "_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, event.getComponentId())) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                NewStuffHelper.buttonPagination(buttons, null, prefix, 24, 0, true));
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_SYSTEM)
    public static void selectBelkoseaAgentSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(SELECT_SYSTEM.length()));
        Player activePlayer = game.getActivePlayer();
        if (!player.hasUnexhaustedLeader(AGENT)
                || activePlayer == null
                || tile == null
                || tile.getTileModel().isHyperlane()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That system is no longer eligible.");
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, player, player.unsafeGetLeader(AGENT));
        BelkoseaAgentService.ignoreOtherShips(game, activePlayer, tile);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " exhausted Razalka Gris for "
                        + activePlayer.getRepresentationNoPing() + ". Other players' ships in "
                        + tile.getRepresentationForButtons(game, player)
                        + " do not affect that player's movement until the end of movement.");
    }

    private static List<Button> getSystemButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile ->
                        !tile.getTileModel().isHyperlane() && FoWHelper.knowsTile(game, player, tile.getPosition()))
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }
}
