package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.strategycard.StrategyCardSecondaryButtonService;

@UtilityClass
public class XinAgentHandler {
    private static final String AGENT = "xinagent";
    private static final String USE_AGENT = "useXinAgent";
    private static final String SELECT_PLAYER = "selectXinAgentPlayer_";

    @ButtonHandler(USE_AGENT)
    public static void useXinAgent(ButtonInteractionEvent event, Game game, Player agentOwner) {
        if (!agentOwner.hasUnexhaustedLeader(AGENT)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Liu must be ready to use this ability.");
            return;
        }
        List<Button> buttons = game.getRealPlayers().stream()
                .map(player -> Buttons.gray(
                        agentOwner.factionButtonChecker() + SELECT_PLAYER + player.getFaction(),
                        player.getFactionNameOrColor(),
                        player.getFactionEmojiOrColor()))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                agentOwner.getRepresentationNoPing() + ", choose the player who may follow an exhausted strategy card.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_PLAYER)
    public static void selectXinAgentPlayer(
            ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_PLAYER.length()));
        if (!agentOwner.hasUnexhaustedLeader(AGENT) || target == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no exhausted strategy cards to follow.");
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, agentOwner, agentOwner.unsafeGetLeader(AGENT));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentation()
                        + ", Liu, the Xin agent, allows you to follow an exhausted strategy card."
                        + " You must still spend 1 command token from your strategy pool to do so.",
                ButtonHelperHeroes.getSecondaryButtons(game));
        ButtonHelper.deleteMessage(event);
    }
}
