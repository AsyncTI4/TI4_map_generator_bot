package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ponthous;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class PonthousLeadersHandler {
    private static final String AGENT_ID = "ponthousagent";
    private static final String USE_AGENT_PREFIX = "ponthousagent_";
    private static final String SELECT_TARGET_PREFIX = "ponthousAgentTarget_";

    public static Button getPonthousAgentButton(Player player, Tile tile) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_AGENT_PREFIX + tile.getPosition(),
                "Use Ponthous Agent",
                FactionEmojis.ponthous);
    }

    @ButtonHandler(USE_AGENT_PREFIX)
    public static void startPonthousAgent(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.substring(USE_AGENT_PREFIX.length());
        if (game == null
                || player == null
                || !player.hasUnexhaustedLeader(AGENT_ID)
                || game.getTileByPosition(position) == null) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        List<Button> buttons = game.getRealPlayers().stream()
                .map(target -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_TARGET_PREFIX + position + "_" + target.getFaction(),
                        target.getColorDisplayName(),
                        target.fogSafeEmoji()))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the player to use _General Caelyn_, the Ponthous agent, on.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_TARGET_PREFIX)
    public static void resolvePonthousAgent(
            ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        String[] parts = buttonID.substring(SELECT_TARGET_PREFIX.length()).split("_", 2);
        if (parts.length != 2 || game == null || agentOwner == null || !agentOwner.hasUnexhaustedLeader(AGENT_ID)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        Player target = game.getPlayerFromColorOrFaction(parts[1]);
        Leader agent = agentOwner.getLeader(AGENT_ID).orElse(null);
        if (tile == null || target == null || agent == null) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                agentOwner.getRepresentation() + " exhausted _General Caelyn_, the Ponthous agent, on "
                        + target.getRepresentation() + ".");

        AddUnitService.addUnits(event, tile, game, target.getColor(), "2 fighter, 2 infantry");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Placed 2 fighters and 2 infantry for "
                        + target.getRepresentationUnfogged()
                        + " in the space area of "
                        + tile.getRepresentationForButtons(game, target)
                        + ".");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
